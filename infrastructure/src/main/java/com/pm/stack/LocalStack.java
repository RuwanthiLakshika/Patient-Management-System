package com.pm.stack;

import software.amazon.awscdk.*;
import software.amazon.awscdk.services.ec2.*;
import software.amazon.awscdk.services.ec2.InstanceType;
import software.amazon.awscdk.services.ecr.Repository;
import software.amazon.awscdk.services.ecs.*;
import software.amazon.awscdk.services.ecs.Protocol;
import software.amazon.awscdk.services.ecs.patterns.ApplicationLoadBalancedFargateService;
import software.amazon.awscdk.services.elasticloadbalancingv2.HealthCheck;
import software.amazon.awscdk.services.logs.LogGroup;
import software.amazon.awscdk.services.logs.RetentionDays;
import software.amazon.awscdk.services.msk.CfnCluster;
import software.amazon.awscdk.services.rds.*;
import software.amazon.awscdk.services.route53.CfnHealthCheck;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class LocalStack extends Stack {
    private final Vpc vpc;
    private final Cluster ecsCluster;
    private final boolean localStackMode;
    private final Map<String, Repository> imageRepositories = new HashMap<>();

    public LocalStack(final App scope, final String id, final StackProps props){
        super(scope, id, props);

        this.localStackMode = Boolean.parseBoolean(System.getenv().getOrDefault("LOCALSTACK_MODE", "false"));
        boolean localStackAlb = Boolean.parseBoolean(System.getenv().getOrDefault("LOCALSTACK_ALB", "false"));
        this.vpc = createVpc();

        if (!localStackMode) {
            createImageRepositories();
        }

        DatabaseInstance authServiceDb = localStackMode
                ? null
                : createDatabase("AuthServiceDB", "auth-service-db");
        DatabaseInstance patientServiceDb = localStackMode
                ? null
                : createDatabase("PatientServiceDB", "patient-service-db");
        CfnHealthCheck authDbHealthCheck = localStackMode
                ? null
                : createDbHealthCheck(authServiceDb, "AuthServiceDBHealthCheck");
        CfnHealthCheck patientDbHealthCheck = localStackMode
                ? null
                : createDbHealthCheck(patientServiceDb, "PatientServiceDBHealthCheck");
        CfnCluster mskCluster = localStackMode ? null : createMskCluster();

        this.ecsCluster = createEcsCluster(localStackMode);

        FargateService authService =
                createFargateService("AuthService",
                        "auth-service",
                        List.of(4005),
                        authServiceDb,
                        localStackMode
                                ? Map.of(
                                        "JWT_SECRET", "Y2hhVEc3aHJnb0hYTzMyZ2ZqVkpiZ1RkZG93YWxrUkM=",
                                        "SPRING_DATASOURCE_URL", "jdbc:postgresql://host.docker.internal:5001/auth_db",
                                        "SPRING_DATASOURCE_USERNAME", "admin_user",
                                        "SPRING_DATASOURCE_PASSWORD", "password"
                                )
                                : Map.of("JWT_SECRET", "Y2hhVEc3aHJnb0hYTzMyZ2ZqVkpiZ1RkZG93YWxrUkM="));

        if (!localStackMode) {
            authService.getNode().addDependency(authDbHealthCheck);
            authService.getNode().addDependency(authServiceDb);
        }

        FargateService billingService =
                createFargateService("BillingService",
                        "billing-service",
                        List.of(4001,9001),
                        null,
                        null);

        FargateService analyticsService =
                createFargateService("AnalyticsService",
                        "analytics-service",
                        List.of(4002),
                        null,
                        null);

        if (!localStackMode) {
            analyticsService.getNode().addDependency(mskCluster);
        }

        FargateService patientService = createFargateService("PatientService",
                "patient-service",
                List.of(4000),
                patientServiceDb,
                localStackMode
                        ? Map.of(
                                "BILLING_SERVICE_ADDRESS", "host.docker.internal",
                                "BILLING_SERVICE_GRPC_PORT", "9001",
                                "SPRING_DATASOURCE_URL", "jdbc:postgresql://host.docker.internal:5000/db",
                                "SPRING_DATASOURCE_USERNAME", "admin_user",
                                "SPRING_DATASOURCE_PASSWORD", "password"
                        )
                        : Map.of(
                                "BILLING_SERVICE_ADDRESS", "host.docker.internal",
                                "BILLING_SERVICE_GRPC_PORT", "9001"
                        ));
        if (!localStackMode) {
            patientService.getNode().addDependency(patientServiceDb);
            patientService.getNode().addDependency(patientDbHealthCheck);
        }
        patientService.getNode().addDependency(billingService);
        if (!localStackMode) {
            patientService.getNode().addDependency(mskCluster);
        }

        if (!localStackMode || localStackAlb) {
            createApiGatewayService();
        }
    }

    private Vpc createVpc(){
        return Vpc.Builder
                .create(this, "PatientManagementVPC")
                .vpcName("PatientManagementVPC")
                .maxAzs(2)
                .build();
    }

    private DatabaseInstance createDatabase(String id, String dbName){
        return DatabaseInstance.Builder
                .create(this, id)
                .engine(DatabaseInstanceEngine.postgres(
                        PostgresInstanceEngineProps.builder()
                                .version(PostgresEngineVersion.VER_17_2)
                                .build()))
                .vpc(vpc)
                .instanceType(InstanceType.of(InstanceClass.BURSTABLE2, InstanceSize.MICRO))
                .allocatedStorage(20)
                .credentials(Credentials.fromGeneratedSecret("admin_user"))
                .databaseName(dbName)
                .removalPolicy(RemovalPolicy.DESTROY)
                .build();
    }

    private CfnHealthCheck createDbHealthCheck(DatabaseInstance db, String id){
        return CfnHealthCheck.Builder.create(this, id)
                .healthCheckConfig(CfnHealthCheck.HealthCheckConfigProperty.builder()
                        .type("TCP")
                        .port(Token.asNumber(db.getDbInstanceEndpointPort()))
                        .ipAddress(db.getDbInstanceEndpointAddress())
                        .requestInterval(30)
                        .failureThreshold(3)
                        .build())
                .build();
    }

    private CfnCluster createMskCluster(){
        return CfnCluster.Builder.create(this, "MskCluster")
                .clusterName("kafa-cluster")
                .kafkaVersion("2.8.0")
                .numberOfBrokerNodes(1)
                .brokerNodeGroupInfo(CfnCluster.BrokerNodeGroupInfoProperty.builder()
                        .instanceType("kafka.m5.xlarge")
                        .clientSubnets(vpc.getPrivateSubnets().stream()
                                .map(ISubnet::getSubnetId)
                                .collect(Collectors.toList()))
                        .brokerAzDistribution("DEFAULT")
                        .build())
                .build();
    }

    private Cluster createEcsCluster(boolean localStackMode){
        Cluster.Builder cluster = Cluster.Builder.create(this, "PatientManagementCluster")
                .vpc(vpc);

        if (!localStackMode) {
            cluster.defaultCloudMapNamespace(CloudMapNamespaceOptions.builder()
                    .name("patient-management.local")
                    .build());
        }

        return cluster.build();
    }

    private FargateService createFargateService(String id,
                                                String imageName,
                                                List<Integer> ports,
                                                DatabaseInstance db,
                                                Map<String, String> additionalEnvVars) {

        FargateTaskDefinition taskDefinition =
                FargateTaskDefinition.Builder.create(this, id + "Task")
                        .cpu(256)
                        .memoryLimitMiB(512)
                        .build();

        ContainerDefinitionOptions.Builder containerOptions =
                ContainerDefinitionOptions.builder()
                        .image(containerImage(imageName))
                        .portMappings(ports.stream()
                                .map(port -> PortMapping.builder()
                                        .containerPort(port)
                                        .hostPort(port)
                                        .protocol(software.amazon.awscdk.services.ecs.Protocol.TCP)
                                        .build())
                                .toList())
                        .logging(LogDriver.awsLogs(AwsLogDriverProps.builder()
                                .logGroup(LogGroup.Builder.create(this, id + "LogGroup")
                                        .logGroupName("/ecs/" + imageName)
                                        .removalPolicy(RemovalPolicy.DESTROY)
                                        .retention(RetentionDays.ONE_DAY)
                                        .build())
                                .streamPrefix(imageName)
                                .build()));

        Map<String, String> envVars = new HashMap<>();
        envVars.put("SPRING_KAFKA_BOOTSTRAP_SERVERS", "localhost.localstack.cloud:4510, localhost.localstack.cloud:4511, localhost.localstack.cloud:4512");

        if(additionalEnvVars != null){
            envVars.putAll(additionalEnvVars);
        }

        if(db != null){
            envVars.put("SPRING_DATASOURCE_URL", "jdbc:postgresql://%s:%s/%s-db".formatted(
                    db.getDbInstanceEndpointAddress(),
                    db.getDbInstanceEndpointPort(),
                    imageName
            ));
            envVars.put("SPRING_DATASOURCE_USERNAME", "admin_user");
            envVars.put("SPRING_DATASOURCE_PASSWORD",
                    db.getSecret().secretValueFromJson("password").toString());
            envVars.put("SPRING_JPA_HIBERNATE_DDL_AUTO", "update");
            envVars.put("SPRING_SQL_INIT_MODE", "always");
            envVars.put("SPRING_DATASOURCE_HIKARI_INITIALIZATION_FAIL_TIMEOUT", "60000");
        }

        containerOptions.environment(envVars);
        taskDefinition.addContainer(imageName + "Container", containerOptions.build());

        return FargateService.Builder.create(this, id)
                .cluster(ecsCluster)
                .taskDefinition(taskDefinition)
                .assignPublicIp(false)
                .serviceName(imageName)
                .build();
    }

    private void createApiGatewayService() {
        FargateTaskDefinition taskDefinition =
                FargateTaskDefinition.Builder.create(this, "APIGatewayTaskDefinition")
                        .cpu(256)
                        .memoryLimitMiB(512)
                        .build();

        ContainerDefinitionOptions containerOptions =
                ContainerDefinitionOptions.builder()
                        .image(containerImage("api-gateway"))
                        .environment(Map.of(
                                "SPRING_PROFILES_ACTIVE", "prod",
                                "AUTH_SERVICE_URL", "http://host.docker.internal:4005"
                        ))
                        .portMappings(List.of(4004).stream()
                                .map(port -> PortMapping.builder()
                                        .containerPort(port)
                                        .hostPort(port)
                                        .protocol(Protocol.TCP)
                                        .build())
                                .toList())
                        .logging(LogDriver.awsLogs(AwsLogDriverProps.builder()
                                .logGroup(LogGroup.Builder.create(this, "ApiGatewayLogGroup")
                                        .logGroupName("/ecs/api-gateway")
                                        .removalPolicy(RemovalPolicy.DESTROY)
                                        .retention(RetentionDays.ONE_DAY)
                                        .build())
                                .streamPrefix("api-gateway")
                                .build()))
                        .build();


        taskDefinition.addContainer("APIGatewayContainer", containerOptions);

        ApplicationLoadBalancedFargateService apiGateway =
                ApplicationLoadBalancedFargateService.Builder.create(this, "APIGatewayService")
                        .cluster(ecsCluster)
                        .serviceName("api-gateway")
                        .taskDefinition(taskDefinition)
                        .desiredCount(1)
                        .healthCheckGracePeriod(Duration.seconds(60))
                        .build();
        apiGateway.getTargetGroup().configureHealthCheck(HealthCheck.builder()
                .path("/actuator/health")
                .healthyHttpCodes("200")
                .build());
    }

    private void createImageRepositories() {
        List.of("auth-service", "billing-service", "analytics-service", "patient-service", "api-gateway")
                .forEach(imageName -> imageRepositories.put(
                        imageName,
                        Repository.Builder.create(this, imageName + "Repository")
                                .repositoryName(imageName)
                                .imageScanOnPush(true)
                                .build()
                ));
    }

    private ContainerImage containerImage(String imageName) {
        if (localStackMode) {
            return ContainerImage.fromRegistry(imageName);
        }
        Repository repository = imageRepositories.get(imageName);
        if (repository == null) {
            throw new IllegalStateException("No ECR repository configured for image: " + imageName);
        }
        return ContainerImage.fromEcrRepository(repository, "latest");
    }

    public static void main(final String[] args) {
        App app = new App(AppProps.builder().outdir("./cdk.out").build());

        StackProps props = StackProps.builder()
                .synthesizer(new BootstraplessSynthesizer())
                .build();

        new LocalStack(app, "localstack", props);
        app.synth();
        System.out.println("App synthesizing in progress...");
    }
}
