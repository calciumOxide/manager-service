package io.choerodon.manager.app.service.impl

import com.netflix.appinfo.InstanceInfo
import com.netflix.appinfo.LeaseInfo
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import io.choerodon.core.exception.CommonException
import io.choerodon.manager.IntegrationTestConfiguration
import io.choerodon.manager.api.dto.InstanceDTO
import io.choerodon.manager.app.service.InstanceService
import io.choerodon.manager.infra.feign.ConfigServerClient
import io.choerodon.manager.infra.mapper.ConfigMapper
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.cloud.client.DefaultServiceInstance
import org.springframework.cloud.client.ServiceInstance
import org.springframework.cloud.client.discovery.DiscoveryClient
import org.springframework.cloud.netflix.eureka.EurekaDiscoveryClient
import org.springframework.context.annotation.Import
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.client.RestTemplate
import spock.lang.Specification

import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT

/**
 * @author Eugen
 */
@SpringBootTest(webEnvironment = RANDOM_PORT)
@Import(IntegrationTestConfiguration)
class InstanceServiceImplSpec extends Specification {
    @Autowired
    InstanceService instanceService

    private RestTemplate restTemplate = Mock(RestTemplate)
    private ConfigServerClient mockConfigServerClient = Mock(ConfigServerClient)

    private DiscoveryClient mockDiscoveryClient = Mock(DiscoveryClient)

    private ConfigMapper mockConfigMapper = Mock(ConfigMapper)

    def setup() {
        instanceService = new InstanceServiceImpl(mockConfigServerClient, mockDiscoveryClient, mockConfigMapper)
        instanceService.setRestTemplate(restTemplate)
    }

    def "Query"() {
        given: '创建参数'
        def instanceDTO = new InstanceDTO("test_server:test_ip:test_port", null, null, null, null, null)
        def instanceId = instanceDTO.getInstanceId()
        def wrongInstanceId = 'test_server:wrong'
        def service = 'test_service'
        def serviceInstance = new DefaultServiceInstance("", "", 1, true)

        def serviceList = new ArrayList<String>()
        serviceList.add(service)
        def serviceInstanceList = new ArrayList<ServiceInstance>()
        serviceInstanceList.add(serviceInstance)

        when: '根据instanceId查询Instance-illegal'
        instanceService.query(wrongInstanceId)

        then: '结果分析'
        def illegalInstanceId = thrown(CommonException)
        illegalInstanceId.message == "error.illegal.instanceId"

        when: '根据instanceId查询Instance'
        mockDiscoveryClient.getServices() >> { return serviceList }
        mockDiscoveryClient.getInstances(_) >> { return serviceInstanceList }
        instanceService.query(instanceId)
        then: '分析结果'
        noExceptionThrown()
    }

    def "fetchEnvInfo[illegalURL]"() {
        given: '创建参数'
        def instanceDTO = new InstanceDTO("test_server:test_ip:test_port", null, null, null, null, null, null)
        def instanceId = instanceDTO.getInstanceId()
        def service = 'test_service'
        def wrongUrlInstanceInfo = new InstanceInfo(instanceId: "test_ip:test_server:test_port", healthCheckUrl: "wrong://111.111.11.111:1111/")
        def leaseInfo = new LeaseInfo(registrationTimestamp: 1L)
        wrongUrlInstanceInfo.setLeaseInfo(leaseInfo)
        def serviceInstance = new EurekaDiscoveryClient.EurekaServiceInstance(wrongUrlInstanceInfo)
        def serviceList = new ArrayList<String>()
        serviceList.add(service)
        def serviceInstanceList = new ArrayList<ServiceInstance>()
        serviceInstanceList.add(serviceInstance)
        when: '根据instanceId查询Instance-urlIllegal'
        mockDiscoveryClient.getServices() >> { return serviceList }
        mockDiscoveryClient.getInstances(_) >> { return serviceInstanceList }
        instanceService.query(instanceId)
        then: '分析结果'
        def fetchEnv = thrown(CommonException)
        fetchEnv.message == "error.illegal.management.url"
    }

    def "fetchEnvInfo[can not fetch env info]"() {
        given: '创建参数'
        def instanceId = 'test_server:test_ip:test_port'
        def service = 'test_service'
        def instanceInfo = new InstanceInfo(instanceId: "test_ip:test_server:test_port", healthCheckUrl: "http://111.111.11.111:1111/")
        def leaseInfo = new LeaseInfo(registrationTimestamp: 1L)
        instanceInfo.setLeaseInfo(leaseInfo)
        def serviceInstance = new EurekaDiscoveryClient.EurekaServiceInstance(instanceInfo)

        def serviceList = new ArrayList<String>()
        serviceList.add(service)
        def serviceInstanceList = new ArrayList<ServiceInstance>()
        serviceInstanceList.add(serviceInstance)

        when: '根据instanceId查询Instance'
        mockDiscoveryClient.getServices() >> { return serviceList }
        mockDiscoveryClient.getInstances(_) >> { return serviceInstanceList }
        instanceService.query(instanceId)
        then: '分析结果'
        def fetchEnv = thrown(CommonException)
        fetchEnv.message == "error.config.fetchEnv"
    }

    def "fetchEnvInfo[HttpStatus.NOT_FOUND]"() {
        given: '创建参数'
        def instanceId = 'test_server:test_ip:test_port'
        def service = 'test_service'
        def instanceInfo = new InstanceInfo(instanceId: "test_ip:test_server:test_port", healthCheckUrl: "http://111.111.11.111:1111/")
        def leaseInfo = new LeaseInfo(registrationTimestamp: 1L)
        instanceInfo.setLeaseInfo(leaseInfo)
        def serviceInstance = new EurekaDiscoveryClient.EurekaServiceInstance(instanceInfo)

        def serviceList = new ArrayList<String>()
        serviceList.add(service)
        def serviceInstanceList = new ArrayList<ServiceInstance>()
        serviceInstanceList.add(serviceInstance)

        and: 'mock'
        def response = new ResponseEntity<String>(HttpStatus.NOT_FOUND)
        restTemplate.getForEntity(_, _) >> { return response }

        when: '根据instanceId查询Instance'
        mockDiscoveryClient.getServices() >> { return serviceList }
        mockDiscoveryClient.getInstances(_) >> { return serviceInstanceList }
        instanceService.query(instanceId)
        then: '分析结果'
        def fetchEnv = thrown(CommonException)
        fetchEnv.message == "error.config.fetchEnv"
    }

    def "processEnvJson"() {
        given: '创建参数'
        def instanceId = 'test_server:test_ip:test_port'
        def service = 'test_service'
        def instanceInfo = new InstanceInfo(instanceId: "test_ip:test_server:test_port", healthCheckUrl: "http://111.111.11.111:1111/")
        def leaseInfo = new LeaseInfo(registrationTimestamp: 1L)
        instanceInfo.setLeaseInfo(leaseInfo)
        def serviceInstance = new EurekaDiscoveryClient.EurekaServiceInstance(instanceInfo)

        def serviceList = new ArrayList<String>()
        serviceList.add(service)
        def serviceInstanceList = new ArrayList<ServiceInstance>()
        serviceInstanceList.add(serviceInstance)
        String body = "{\"activeProfiles\":[],\"propertySources\":[{\"name\":\"server.ports\",\"properties\":{\"local.management.port\":{\"value\":8964},\"local.server.port\":{\"value\":8963}}},{\"name\":\"servletContextInitParams\",\"properties\":{}},{\"name\":\"systemProperties\",\"properties\":{\"java.runtime.name\":{\"value\":\"Java(TM) SE Runtime Environment\"},\"spring.output.ansi.enabled\":{\"value\":\"always\"},\"sun.boot.library.path\":{\"value\":\"/Library/Java/JavaVirtualMachines/jdk1.8.0_192.jdk/Contents/Home/jre/lib\"},\"java.vm.version\":{\"value\":\"25.192-b12\"},\"gopherProxySet\":{\"value\":\"false\"},\"rebel.env.ide.product\":{\"value\":\"IU\"},\"java.vm.vendor\":{\"value\":\"Oracle Corporation\"},\"java.vendor.url\":{\"value\":\"http://java.oracle.com/\"},\"java.rmi.server.randomIDs\":{\"value\":\"true\"},\"path.separator\":{\"value\":\":\"},\"java.vm.name\":{\"value\":\"Java HotSpot(TM) 64-Bit Server VM\"},\"file.encoding.pkg\":{\"value\":\"sun.io\"},\"user.country\":{\"value\":\"CN\"},\"sun.java.launcher\":{\"value\":\"SUN_STANDARD\"},\"sun.os.patch.level\":{\"value\":\"unknown\"},\"rebel.base\":{\"value\":\"/Users/superlee/.jrebel\"},\"PID\":{\"value\":\"11485\"},\"java.vm.specification.name\":{\"value\":\"Java Virtual Machine Specification\"},\"user.dir\":{\"value\":\"/Users/superlee/Documents/idea_work/framework-group/manager-service\"},\"intellij.debug.agent\":{\"value\":\"true\"},\"java.runtime.version\":{\"value\":\"1.8.0_192-b12\"},\"java.awt.graphicsenv\":{\"value\":\"sun.awt.CGraphicsEnvironment\"},\"java.endorsed.dirs\":{\"value\":\"/Library/Java/JavaVirtualMachines/jdk1.8.0_192.jdk/Contents/Home/jre/lib/endorsed\"},\"os.arch\":{\"value\":\"x86_64\"},\"rebel.env.ide.version\":{\"value\":\"2018.3.4\"},\"rebel.native.image\":{\"value\":\"/var/folders/sg/96g3db8s5rs2hxd1g3b4wt080000gn/T/jrebel-JRebel-201902130820-griffin/lib/libjrebel64.dylib\"},\"java.io.tmpdir\":{\"value\":\"/var/folders/sg/96g3db8s5rs2hxd1g3b4wt080000gn/T/\"},\"line.separator\":{\"value\":\"\\n\"},\"java.vm.specification.vendor\":{\"value\":\"Oracle Corporation\"},\"os.name\":{\"value\":\"Mac OS X\"},\"sun.jnu.encoding\":{\"value\":\"UTF-8\"},\"spring.beaninfo.ignore\":{\"value\":\"true\"},\"java.library.path\":{\"value\":\"/Users/superlee/Library/Java/Extensions:/Library/Java/Extensions:/Network/Library/Java/Extensions:/System/Library/Java/Extensions:/usr/lib/java:.\"},\"sun.nio.ch.bugLevel\":{\"value\":\"\"},\"jboss.modules.system.pkgs\":{\"value\":\"com.intellij.rt\"},\"rebel.env.ide\":{\"value\":\"intellij\"},\"java.specification.name\":{\"value\":\"Java Platform API Specification\"},\"java.class.version\":{\"value\":\"52.0\"},\"sun.management.compiler\":{\"value\":\"HotSpot 64-Bit Tiered Compilers\"},\"spring.liveBeansView.mbeanDomain\":{\"value\":\"\"},\"os.version\":{\"value\":\"10.14.5\"},\"user.home\":{\"value\":\"/Users/superlee\"},\"user.timezone\":{\"value\":\"Asia/Shanghai\"},\"java.awt.printerjob\":{\"value\":\"sun.lwawt.macosx.CPrinterJob\"},\"@appId\":{\"value\":\"manager-service\"},\"file.encoding\":{\"value\":\"UTF-8\"},\"java.specification.version\":{\"value\":\"1.8\"},\"java.class.path\":{\"value\":\"/Library/Java/JavaVirtualMachines/jdk1.8.0_192.jdk/Contents/Home/jre/lib/charsets.jar:/Library/Java/JavaVirtualMachines/jdk1.8.0_192.jdk/Contents/Home/jre/lib/deploy.jar:/Library/Java/JavaVirtualMachines/jdk1.8.0_192.jdk/Contents/Home/jre/lib/ext/cldrdata.jar:/Library/Java/JavaVirtualMachines/jdk1.8.0_192.jdk/Contents/Home/jre/lib/ext/dnsns.jar:/Library/Java/JavaVirtualMachines/jdk1.8.0_192.jdk/Contents/Home/jre/lib/ext/jaccess.jar:/Library/Java/JavaVirtualMachines/jdk1.8.0_192.jdk/Contents/Home/jre/lib/ext/jfxrt.jar:/Library/Java/JavaVirtualMachines/jdk1.8.0_192.jdk/Contents/Home/jre/lib/ext/localedata.jar:/Library/Java/JavaVirtualMachines/jdk1.8.0_192.jdk/Contents/Home/jre/lib/ext/nashorn.jar:/Library/Java/JavaVirtualMachines/jdk1.8.0_192.jdk/Contents/Home/jre/lib/ext/sunec.jar:/Library/Java/JavaVirtualMachines/jdk1.8.0_192.jdk/Contents/Home/jre/lib/ext/sunjce_provider.jar:/Library/Java/JavaVirtualMachines/jdk1.8.0_192.jdk/Contents/Home/jre/lib/ext/sunpkcs11.jar:/Library/Java/JavaVirtualMachines/jdk1.8.0_192.jdk/Contents/Home/jre/lib/ext/zipfs.jar:/Library/Java/JavaVirtualMachines/jdk1.8.0_192.jdk/Contents/Home/jre/lib/javaws.jar:/Library/Java/JavaVirtualMachines/jdk1.8.0_192.jdk/Contents/Home/jre/lib/jce.jar:/Library/Java/JavaVirtualMachines/jdk1.8.0_192.jdk/Contents/Home/jre/lib/jfr.jar:/Library/Java/JavaVirtualMachines/jdk1.8.0_192.jdk/Contents/Home/jre/lib/jfxswt.jar:/Library/Java/JavaVirtualMachines/jdk1.8.0_192.jdk/Contents/Home/jre/lib/jsse.jar:/Library/Java/JavaVirtualMachines/jdk1.8.0_192.jdk/Contents/Home/jre/lib/management-agent.jar:/Library/Java/JavaVirtualMachines/jdk1.8.0_192.jdk/Contents/Home/jre/lib/plugin.jar:/Library/Java/JavaVirtualMachines/jdk1.8.0_192.jdk/Contents/Home/jre/lib/resources.jar:/Library/Java/JavaVirtualMachines/jdk1.8.0_192.jdk/Contents/Home/jre/lib/rt.jar:/Library/Java/JavaVirtualMachines/jdk1.8.0_192.jdk/Contents/Home/lib/ant-javafx.jar:/Library/Java/JavaVirtualMachines/jdk1.8.0_192.jdk/Contents/Home/lib/dt.jar:/Library/Java/JavaVirtualMachines/jdk1.8.0_192.jdk/Contents/Home/lib/javafx-mx.jar:/Library/Java/JavaVirtualMachines/jdk1.8.0_192.jdk/Contents/Home/lib/jconsole.jar:/Library/Java/JavaVirtualMachines/jdk1.8.0_192.jdk/Contents/Home/lib/packager.jar:/Library/Java/JavaVirtualMachines/jdk1.8.0_192.jdk/Contents/Home/lib/sa-jdi.jar:/Library/Java/JavaVirtualMachines/jdk1.8.0_192.jdk/Contents/Home/lib/tools.jar:/Users/superlee/Documents/idea_work/framework-group/manager-service/target/classes:/Users/superlee/.m2/repository/org/springframework/boot/spring-boot-starter-undertow/2.0.6.RELEASE/spring-boot-starter-undertow-2.0.6.RELEASE.jar:/Users/superlee/.m2/repository/io/undertow/undertow-core/1.4.26.Final/undertow-core-1.4.26.Final.jar:/Users/superlee/.m2/repository/org/jboss/logging/jboss-logging/3.3.2.Final/jboss-logging-3.3.2.Final.jar:/Users/superlee/.m2/repository/org/jboss/xnio/xnio-api/3.3.8.Final/xnio-api-3.3.8.Final.jar:/Users/superlee/.m2/repository/org/jboss/xnio/xnio-nio/3.3.8.Final/xnio-nio-3.3.8.Final.jar:/Users/superlee/.m2/repository/io/undertow/undertow-servlet/1.4.26.Final/undertow-servlet-1.4.26.Final.jar:/Users/superlee/.m2/repository/org/jboss/spec/javax/annotation/jboss-annotations-api_1.2_spec/1.0.2.Final/jboss-annotations-api_1.2_spec-1.0.2.Final.jar:/Users/superlee/.m2/repository/io/undertow/undertow-websockets-jsr/1.4.26.Final/undertow-websockets-jsr-1.4.26.Final.jar:/Users/superlee/.m2/repository/org/jboss/spec/javax/websocket/jboss-websocket-api_1.1_spec/1.1.3.Final/jboss-websocket-api_1.1_spec-1.1.3.Final.jar:/Users/superlee/.m2/repository/javax/servlet/javax.servlet-api/3.1.0/javax.servlet-api-3.1.0.jar:/Users/superlee/.m2/repository/org/glassfish/javax.el/3.0.0/javax.el-3.0.0.jar:/Users/superlee/.m2/repository/org/springframework/boot/spring-boot-starter-web/2.0.6.RELEASE/spring-boot-starter-web-2.0.6.RELEASE.jar:/Users/superlee/.m2/repository/org/springframework/boot/spring-boot-starter/2.0.6.RELEASE/spring-boot-starter-2.0.6.RELEASE.jar:/Users/superlee/.m2/repository/org/springframework/boot/spring-boot/2.0.6.RELEASE/spring-boot-2.0.6.RELEASE.jar:/Users/superlee/.m2/repository/org/springframework/boot/spring-boot-starter-logging/2.0.6.RELEASE/spring-boot-starter-logging-2.0.6.RELEASE.jar:/Users/superlee/.m2/repository/ch/qos/logback/logback-classic/1.2.3/logback-classic-1.2.3.jar:/Users/superlee/.m2/repository/ch/qos/logback/logback-core/1.2.3/logback-core-1.2.3.jar:/Users/superlee/.m2/repository/org/apache/logging/log4j/log4j-to-slf4j/2.10.0/log4j-to-slf4j-2.10.0.jar:/Users/superlee/.m2/repository/org/apache/logging/log4j/log4j-api/2.10.0/log4j-api-2.10.0.jar:/Users/superlee/.m2/repository/org/slf4j/jul-to-slf4j/1.7.25/jul-to-slf4j-1.7.25.jar:/Users/superlee/.m2/repository/javax/annotation/javax.annotation-api/1.3.2/javax.annotation-api-1.3.2.jar:/Users/superlee/.m2/repository/org/springframework/boot/spring-boot-starter-json/2.0.6.RELEASE/spring-boot-starter-json-2.0.6.RELEASE.jar:/Users/superlee/.m2/repository/com/fasterxml/jackson/datatype/jackson-datatype-jdk8/2.9.7/jackson-datatype-jdk8-2.9.7.jar:/Users/superlee/.m2/repository/com/fasterxml/jackson/module/jackson-module-parameter-names/2.9.7/jackson-module-parameter-names-2.9.7.jar:/Users/superlee/.m2/repository/org/hibernate/validator/hibernate-validator/6.0.13.Final/hibernate-validator-6.0.13.Final.jar:/Users/superlee/.m2/repository/com/fasterxml/classmate/1.3.4/classmate-1.3.4.jar:/Users/superlee/.m2/repository/org/springframework/spring-web/5.0.10.RELEASE/spring-web-5.0.10.RELEASE.jar:/Users/superlee/.m2/repository/org/springframework/spring-beans/5.0.10.RELEASE/spring-beans-5.0.10.RELEASE.jar:/Users/superlee/.m2/repository/org/springframework/spring-webmvc/5.0.10.RELEASE/spring-webmvc-5.0.10.RELEASE.jar:/Users/superlee/.m2/repository/org/springframework/spring-context/5.0.10.RELEASE/spring-context-5.0.10.RELEASE.jar:/Users/superlee/.m2/repository/org/springframework/spring-expression/5.0.10.RELEASE/spring-expression-5.0.10.RELEASE.jar:/Users/superlee/.m2/repository/io/choerodon/choerodon-starter-oauth-resource/0.11.2.RELEASE/choerodon-starter-oauth-resource-0.11.2.RELEASE.jar:/Users/superlee/.m2/repository/org/springframework/security/oauth/spring-security-oauth2/2.3.5.RELEASE/spring-security-oauth2-2.3.5.RELEASE.jar:/Users/superlee/.m2/repository/org/springframework/security/spring-security-core/5.0.9.RELEASE/spring-security-core-5.0.9.RELEASE.jar:/Users/superlee/.m2/repository/org/springframework/security/spring-security-config/5.0.9.RELEASE/spring-security-config-5.0.9.RELEASE.jar:/Users/superlee/.m2/repository/org/springframework/security/spring-security-web/5.0.9.RELEASE/spring-security-web-5.0.9.RELEASE.jar:/Users/superlee/.m2/repository/commons-codec/commons-codec/1.11/commons-codec-1.11.jar:/Users/superlee/.m2/repository/org/codehaus/jackson/jackson-mapper-asl/1.9.13/jackson-mapper-asl-1.9.13.jar:/Users/superlee/.m2/repository/org/codehaus/jackson/jackson-core-asl/1.9.13/jackson-core-asl-1.9.13.jar:/Users/superlee/.m2/repository/org/springframework/boot/spring-boot-starter-security/2.0.6.RELEASE/spring-boot-starter-security-2.0.6.RELEASE.jar:/Users/superlee/.m2/repository/org/springframework/security/spring-security-jwt/1.0.10.RELEASE/spring-security-jwt-1.0.10.RELEASE.jar:/Users/superlee/.m2/repository/org/bouncycastle/bcpkix-jdk15on/1.60/bcpkix-jdk15on-1.60.jar:/Users/superlee/.m2/repository/org/bouncycastle/bcprov-jdk15on/1.60/bcprov-jdk15on-1.60.jar:/Users/superlee/.m2/repository/com/fasterxml/jackson/datatype/jackson-datatype-jsr310/2.9.7/jackson-datatype-jsr310-2.9.7.jar:/Users/superlee/.m2/repository/com/fasterxml/jackson/core/jackson-annotations/2.9.0/jackson-annotations-2.9.0.jar:/Users/superlee/.m2/repository/org/springframework/spring-tx/5.0.10.RELEASE/spring-tx-5.0.10.RELEASE.jar:/Users/superlee/.m2/repository/io/choerodon/choerodon-starter-base/0.11.2.RELEASE/choerodon-starter-base-0.11.2.RELEASE.jar:/Users/superlee/.m2/repository/javax/persistence/persistence-api/1.0/persistence-api-1.0.jar:/Users/superlee/.m2/repository/org/springframework/spring-jdbc/5.0.10.RELEASE/spring-jdbc-5.0.10.RELEASE.jar:/Users/superlee/.m2/repository/io/choerodon/choerodon-starter-swagger/0.11.2.RELEASE/choerodon-starter-swagger-0.11.2.RELEASE.jar:/Users/superlee/.m2/repository/io/springfox/springfox-swagger2/2.6.1/springfox-swagger2-2.6.1.jar:/Users/superlee/.m2/repository/io/swagger/swagger-annotations/1.5.10/swagger-annotations-1.5.10.jar:/Users/superlee/.m2/repository/io/swagger/swagger-models/1.5.10/swagger-models-1.5.10.jar:/Users/superlee/.m2/repository/io/springfox/springfox-spi/2.6.1/springfox-spi-2.6.1.jar:/Users/superlee/.m2/repository/io/springfox/springfox-core/2.6.1/springfox-core-2.6.1.jar:/Users/superlee/.m2/repository/io/springfox/springfox-schema/2.6.1/springfox-schema-2.6.1.jar:/Users/superlee/.m2/repository/io/springfox/springfox-swagger-common/2.6.1/springfox-swagger-common-2.6.1.jar:/Users/superlee/.m2/repository/org/springframework/plugin/spring-plugin-core/1.2.0.RELEASE/spring-plugin-core-1.2.0.RELEASE.jar:/Users/superlee/.m2/repository/org/springframework/plugin/spring-plugin-metadata/1.2.0.RELEASE/spring-plugin-metadata-1.2.0.RELEASE.jar:/Users/superlee/.m2/repository/org/mapstruct/mapstruct/1.0.0.Final/mapstruct-1.0.0.Final.jar:/Users/superlee/.m2/repository/com/fasterxml/jackson/core/jackson-databind/2.9.7/jackson-databind-2.9.7.jar:/Users/superlee/.m2/repository/io/choerodon/choerodon-starter-actuator/0.11.2.RELEASE/choerodon-starter-actuator-0.11.2.RELEASE.jar:/Users/superlee/.m2/repository/io/choerodon/choerodon-annotation-processor/0.11.2.RELEASE/choerodon-annotation-processor-0.11.2.RELEASE.jar:/Users/superlee/.m2/repository/org/springframework/boot/spring-boot-starter-jdbc/2.0.6.RELEASE/spring-boot-starter-jdbc-2.0.6.RELEASE.jar:/Users/superlee/.m2/repository/com/zaxxer/HikariCP/2.7.9/HikariCP-2.7.9.jar:/Users/superlee/.m2/repository/io/choerodon/choerodon-starter-asgard/0.11.2.RELEASE/choerodon-starter-asgard-0.11.2.RELEASE.jar:/Users/superlee/.m2/repository/io/choerodon/choerodon-starter-core/0.11.2.RELEASE/choerodon-starter-core-0.11.2.RELEASE.jar:/Users/superlee/.m2/repository/org/springframework/cloud/spring-cloud-context/2.0.2.RELEASE/spring-cloud-context-2.0.2.RELEASE.jar:/Users/superlee/.m2/repository/org/springframework/security/spring-security-crypto/5.0.9.RELEASE/spring-security-crypto-5.0.9.RELEASE.jar:/Users/superlee/.m2/repository/javax/validation/validation-api/2.0.1.Final/validation-api-2.0.1.Final.jar:/Users/superlee/.m2/repository/org/apache/poi/poi/3.16/poi-3.16.jar:/Users/superlee/.m2/repository/org/apache/commons/commons-collections4/4.1/commons-collections4-4.1.jar:/Users/superlee/.m2/repository/org/apache/poi/poi-ooxml/3.16/poi-ooxml-3.16.jar:/Users/superlee/.m2/repository/org/apache/poi/poi-ooxml-schemas/3.16/poi-ooxml-schemas-3.16.jar:/Users/superlee/.m2/repository/org/apache/xmlbeans/xmlbeans/2.6.0/xmlbeans-2.6.0.jar:/Users/superlee/.m2/repository/com/github/virtuald/curvesapi/1.04/curvesapi-1.04.jar:/Users/superlee/.m2/repository/com/github/pagehelper/pagehelper/5.1.8/pagehelper-5.1.8.jar:/Users/superlee/.m2/repository/com/github/jsqlparser/jsqlparser/1.2/jsqlparser-1.2.jar:/Users/superlee/.m2/repository/io/springfox/springfox-swagger-ui/2.6.1/springfox-swagger-ui-2.6.1.jar:/Users/superlee/.m2/repository/io/springfox/springfox-spring-web/2.6.1/springfox-spring-web-2.6.1.jar:/Users/superlee/.m2/repository/io/choerodon/choerodon-starter-mybatis/0.11.2.RELEASE/choerodon-starter-mybatis-0.11.2.RELEASE.jar:/Users/superlee/.m2/repository/tk/mybatis/mapper-spring-boot-starter/2.1.5/mapper-spring-boot-starter-2.1.5.jar:/Users/superlee/.m2/repository/org/mybatis/mybatis/3.4.6/mybatis-3.4.6.jar:/Users/superlee/.m2/repository/org/mybatis/mybatis-spring/1.3.2/mybatis-spring-1.3.2.jar:/Users/superlee/.m2/repository/tk/mybatis/mapper-core/1.1.5/mapper-core-1.1.5.jar:/Users/superlee/.m2/repository/tk/mybatis/mapper-base/1.1.5/mapper-base-1.1.5.jar:/Users/superlee/.m2/repository/tk/mybatis/mapper-weekend/1.1.5/mapper-weekend-1.1.5.jar:/Users/superlee/.m2/repository/tk/mybatis/mapper-spring/1.1.5/mapper-spring-1.1.5.jar:/Users/superlee/.m2/repository/tk/mybatis/mapper-extra/1.1.5/mapper-extra-1.1.5.jar:/Users/superlee/.m2/repository/tk/mybatis/mapper-spring-boot-autoconfigure/2.1.5/mapper-spring-boot-autoconfigure-2.1.5.jar:/Users/superlee/.m2/repository/com/github/pagehelper/pagehelper-spring-boot-starter/1.2.10/pagehelper-spring-boot-starter-1.2.10.jar:/Users/superlee/.m2/repository/org/mybatis/spring/boot/mybatis-spring-boot-starter/1.3.2/mybatis-spring-boot-starter-1.3.2.jar:/Users/superlee/.m2/repository/org/mybatis/spring/boot/mybatis-spring-boot-autoconfigure/1.3.2/mybatis-spring-boot-autoconfigure-1.3.2.jar:/Users/superlee/.m2/repository/com/github/pagehelper/pagehelper-spring-boot-autoconfigure/1.2.10/pagehelper-spring-boot-autoconfigure-1.2.10.jar:/Users/superlee/.m2/repository/org/hibernate/javax/persistence/hibernate-jpa-2.0-api/1.0.1.Final/hibernate-jpa-2.0-api-1.0.1.Final.jar:/Users/superlee/.m2/repository/org/springframework/boot/spring-boot-starter-data-redis/2.0.6.RELEASE/spring-boot-starter-data-redis-2.0.6.RELEASE.jar:/Users/superlee/.m2/repository/org/springframework/data/spring-data-redis/2.0.11.RELEASE/spring-data-redis-2.0.11.RELEASE.jar:/Users/superlee/.m2/repository/org/springframework/data/spring-data-keyvalue/2.0.11.RELEASE/spring-data-keyvalue-2.0.11.RELEASE.jar:/Users/superlee/.m2/repository/org/springframework/data/spring-data-commons/2.0.11.RELEASE/spring-data-commons-2.0.11.RELEASE.jar:/Users/superlee/.m2/repository/org/springframework/spring-oxm/5.0.10.RELEASE/spring-oxm-5.0.10.RELEASE.jar:/Users/superlee/.m2/repository/org/springframework/spring-context-support/5.0.10.RELEASE/spring-context-support-5.0.10.RELEASE.jar:/Users/superlee/.m2/repository/io/lettuce/lettuce-core/5.0.5.RELEASE/lettuce-core-5.0.5.RELEASE.jar:/Users/superlee/.m2/repository/io/projectreactor/reactor-core/3.1.10.RELEASE/reactor-core-3.1.10.RELEASE.jar:/Users/superlee/.m2/repository/org/reactivestreams/reactive-streams/1.0.2/reactive-streams-1.0.2.jar:/Users/superlee/.m2/repository/io/netty/netty-common/4.1.29.Final/netty-common-4.1.29.Final.jar:/Users/superlee/.m2/repository/io/netty/netty-transport/4.1.29.Final/netty-transport-4.1.29.Final.jar:/Users/superlee/.m2/repository/io/netty/netty-buffer/4.1.29.Final/netty-buffer-4.1.29.Final.jar:/Users/superlee/.m2/repository/io/netty/netty-resolver/4.1.29.Final/netty-resolver-4.1.29.Final.jar:/Users/superlee/.m2/repository/io/netty/netty-handler/4.1.29.Final/netty-handler-4.1.29.Final.jar:/Users/superlee/.m2/repository/io/netty/netty-codec/4.1.29.Final/netty-codec-4.1.29.Final.jar:/Users/superlee/.m2/repository/io/choerodon/choerodon-starter-feign-replay/0.11.2.RELEASE/choerodon-starter-feign-replay-0.11.2.RELEASE.jar:/Users/superlee/.m2/repository/org/springframework/cloud/spring-cloud-starter-openfeign/2.0.2.RELEASE/spring-cloud-starter-openfeign-2.0.2.RELEASE.jar:/Users/superlee/.m2/repository/org/springframework/cloud/spring-cloud-openfeign-core/2.0.2.RELEASE/spring-cloud-openfeign-core-2.0.2.RELEASE.jar:/Users/superlee/.m2/repository/io/github/openfeign/form/feign-form-spring/3.3.0/feign-form-spring-3.3.0.jar:/Users/superlee/.m2/repository/io/github/openfeign/form/feign-form/3.3.0/feign-form-3.3.0.jar:/Users/superlee/.m2/repository/com/google/code/findbugs/annotations/3.0.1/annotations-3.0.1.jar:/Users/superlee/.m2/repository/net/jcip/jcip-annotations/1.0/jcip-annotations-1.0.jar:/Users/superlee/.m2/repository/commons-fileupload/commons-fileupload/1.3.3/commons-fileupload-1.3.3.jar:/Users/superlee/.m2/repository/org/springframework/cloud/spring-cloud-commons/2.0.2.RELEASE/spring-cloud-commons-2.0.2.RELEASE.jar:/Users/superlee/.m2/repository/io/github/openfeign/feign-core/9.7.0/feign-core-9.7.0.jar:/Users/superlee/.m2/repository/io/github/openfeign/feign-slf4j/9.7.0/feign-slf4j-9.7.0.jar:/Users/superlee/.m2/repository/io/github/openfeign/feign-hystrix/9.7.0/feign-hystrix-9.7.0.jar:/Users/superlee/.m2/repository/com/netflix/hystrix/hystrix-core/1.5.12/hystrix-core-1.5.12.jar:/Users/superlee/.m2/repository/io/github/openfeign/feign-java8/9.7.0/feign-java8-9.7.0.jar:/Users/superlee/.m2/repository/org/springframework/cloud/spring-cloud-starter-netflix-eureka-client/2.0.2.RELEASE/spring-cloud-starter-netflix-eureka-client-2.0.2.RELEASE.jar:/Users/superlee/.m2/repository/org/springframework/cloud/spring-cloud-starter/2.0.2.RELEASE/spring-cloud-starter-2.0.2.RELEASE.jar:/Users/superlee/.m2/repository/org/springframework/security/spring-security-rsa/1.0.7.RELEASE/spring-security-rsa-1.0.7.RELEASE.jar:/Users/superlee/.m2/repository/org/springframework/cloud/spring-cloud-netflix-core/2.0.2.RELEASE/spring-cloud-netflix-core-2.0.2.RELEASE.jar:/Users/superlee/.m2/repository/org/springframework/cloud/spring-cloud-netflix-eureka-client/2.0.2.RELEASE/spring-cloud-netflix-eureka-client-2.0.2.RELEASE.jar:/Users/superlee/.m2/repository/com/netflix/eureka/eureka-client/1.9.3/eureka-client-1.9.3.jar:/Users/superlee/.m2/repository/org/codehaus/jettison/jettison/1.3.7/jettison-1.3.7.jar:/Users/superlee/.m2/repository/stax/stax-api/1.0.1/stax-api-1.0.1.jar:/Users/superlee/.m2/repository/com/netflix/netflix-commons/netflix-eventbus/0.3.0/netflix-eventbus-0.3.0.jar:/Users/superlee/.m2/repository/com/netflix/netflix-commons/netflix-infix/0.3.0/netflix-infix-0.3.0.jar:/Users/superlee/.m2/repository/commons-jxpath/commons-jxpath/1.3/commons-jxpath-1.3.jar:/Users/superlee/.m2/repository/joda-time/joda-time/2.9.9/joda-time-2.9.9.jar:/Users/superlee/.m2/repository/org/antlr/antlr-runtime/3.4/antlr-runtime-3.4.jar:/Users/superlee/.m2/repository/org/antlr/stringtemplate/3.2.1/stringtemplate-3.2.1.jar:/Users/superlee/.m2/repository/antlr/antlr/2.7.7/antlr-2.7.7.jar:/Users/superlee/.m2/repository/com/google/code/gson/gson/2.8.5/gson-2.8.5.jar:/Users/superlee/.m2/repository/org/apache/commons/commons-math/2.2/commons-math-2.2.jar:/Users/superlee/.m2/repository/com/netflix/archaius/archaius-core/0.7.6/archaius-core-0.7.6.jar:/Users/superlee/.m2/repository/javax/ws/rs/jsr311-api/1.1.1/jsr311-api-1.1.1.jar:/Users/superlee/.m2/repository/com/netflix/servo/servo-core/0.12.21/servo-core-0.12.21.jar:/Users/superlee/.m2/repository/com/sun/jersey/jersey-core/1.19.1/jersey-core-1.19.1.jar:/Users/superlee/.m2/repository/com/sun/jersey/jersey-client/1.19.1/jersey-client-1.19.1.jar:/Users/superlee/.m2/repository/com/sun/jersey/contribs/jersey-apache-client4/1.19.1/jersey-apache-client4-1.19.1.jar:/Users/superlee/.m2/repository/org/apache/httpcomponents/httpclient/4.5.6/httpclient-4.5.6.jar:/Users/superlee/.m2/repository/org/apache/httpcomponents/httpcore/4.4.10/httpcore-4.4.10.jar:/Users/superlee/.m2/repository/com/google/inject/guice/4.1.0/guice-4.1.0.jar:/Users/superlee/.m2/repository/javax/inject/javax.inject/1/javax.inject-1.jar:/Users/superlee/.m2/repository/aopalliance/aopalliance/1.0/aopalliance-1.0.jar:/Users/superlee/.m2/repository/com/github/vlsi/compactmap/compactmap/1.2.1/compactmap-1.2.1.jar:/Users/superlee/.m2/repository/com/github/andrewoma/dexx/dexx-collections/0.2/dexx-collections-0.2.jar:/Users/superlee/.m2/repository/com/netflix/eureka/eureka-core/1.9.3/eureka-core-1.9.3.jar:/Users/superlee/.m2/repository/org/codehaus/woodstox/woodstox-core-asl/4.4.1/woodstox-core-asl-4.4.1.jar:/Users/superlee/.m2/repository/javax/xml/stream/stax-api/1.0-2/stax-api-1.0-2.jar:/Users/superlee/.m2/repository/org/codehaus/woodstox/stax2-api/3.1.4/stax2-api-3.1.4.jar:/Users/superlee/.m2/repository/org/springframework/cloud/spring-cloud-starter-netflix-archaius/2.0.2.RELEASE/spring-cloud-starter-netflix-archaius-2.0.2.RELEASE.jar:/Users/superlee/.m2/repository/org/springframework/cloud/spring-cloud-netflix-ribbon/2.0.2.RELEASE/spring-cloud-netflix-ribbon-2.0.2.RELEASE.jar:/Users/superlee/.m2/repository/org/springframework/cloud/spring-cloud-netflix-archaius/2.0.2.RELEASE/spring-cloud-netflix-archaius-2.0.2.RELEASE.jar:/Users/superlee/.m2/repository/commons-configuration/commons-configuration/1.8/commons-configuration-1.8.jar:/Users/superlee/.m2/repository/commons-lang/commons-lang/2.6/commons-lang-2.6.jar:/Users/superlee/.m2/repository/org/springframework/cloud/spring-cloud-starter-netflix-ribbon/2.0.2.RELEASE/spring-cloud-starter-netflix-ribbon-2.0.2.RELEASE.jar:/Users/superlee/.m2/repository/com/netflix/ribbon/ribbon/2.2.5/ribbon-2.2.5.jar:/Users/superlee/.m2/repository/com/netflix/ribbon/ribbon-transport/2.2.5/ribbon-transport-2.2.5.jar:/Users/superlee/.m2/repository/io/reactivex/rxnetty-contexts/0.4.9/rxnetty-contexts-0.4.9.jar:/Users/superlee/.m2/repository/io/reactivex/rxnetty-servo/0.4.9/rxnetty-servo-0.4.9.jar:/Users/superlee/.m2/repository/io/reactivex/rxnetty/0.4.9/rxnetty-0.4.9.jar:/Users/superlee/.m2/repository/com/netflix/ribbon/ribbon-core/2.2.5/ribbon-core-2.2.5.jar:/Users/superlee/.m2/repository/com/netflix/ribbon/ribbon-httpclient/2.2.5/ribbon-httpclient-2.2.5.jar:/Users/superlee/.m2/repository/com/netflix/netflix-commons/netflix-commons-util/0.3.0/netflix-commons-util-0.3.0.jar:/Users/superlee/.m2/repository/com/netflix/ribbon/ribbon-loadbalancer/2.2.5/ribbon-loadbalancer-2.2.5.jar:/Users/superlee/.m2/repository/com/netflix/netflix-commons/netflix-statistics/0.1.1/netflix-statistics-0.1.1.jar:/Users/superlee/.m2/repository/io/reactivex/rxjava/1.3.8/rxjava-1.3.8.jar:/Users/superlee/.m2/repository/com/netflix/ribbon/ribbon-eureka/2.2.5/ribbon-eureka-2.2.5.jar:/Users/superlee/.m2/repository/com/thoughtworks/xstream/xstream/1.4.10/xstream-1.4.10.jar:/Users/superlee/.m2/repository/xmlpull/xmlpull/1.1.3.1/xmlpull-1.1.3.1.jar:/Users/superlee/.m2/repository/xpp3/xpp3_min/1.1.4c/xpp3_min-1.1.4c.jar:/Users/superlee/.m2/repository/commons-collections/commons-collections/3.2.2/commons-collections-3.2.2.jar:/Users/superlee/.m2/repository/com/google/guava/guava/27.0.1-jre/guava-27.0.1-jre.jar:/Users/superlee/.m2/repository/com/google/guava/failureaccess/1.0.1/failureaccess-1.0.1.jar:/Users/superlee/.m2/repository/com/google/guava/listenablefuture/9999.0-empty-to-avoid-conflict-with-guava/listenablefuture-9999.0-empty-to-avoid-conflict-with-guava.jar:/Users/superlee/.m2/repository/com/google/code/findbugs/jsr305/3.0.2/jsr305-3.0.2.jar:/Users/superlee/.m2/repository/org/checkerframework/checker-qual/2.5.2/checker-qual-2.5.2.jar:/Users/superlee/.m2/repository/com/google/errorprone/error_prone_annotations/2.2.0/error_prone_annotations-2.2.0.jar:/Users/superlee/.m2/repository/com/google/j2objc/j2objc-annotations/1.1/j2objc-annotations-1.1.jar:/Users/superlee/.m2/repository/org/codehaus/mojo/animal-sniffer-annotations/1.17/animal-sniffer-annotations-1.17.jar:/Users/superlee/.m2/repository/io/choerodon/choerodon-starter-eureka-event/0.11.2.RELEASE/choerodon-starter-eureka-event-0.11.2.RELEASE.jar:/Users/superlee/.m2/repository/org/javassist/javassist/3.23.1-GA/javassist-3.23.1-GA.jar:/Users/superlee/.m2/repository/org/springframework/boot/spring-boot-starter-aop/2.0.6.RELEASE/spring-boot-starter-aop-2.0.6.RELEASE.jar:/Users/superlee/.m2/repository/org/springframework/spring-aop/5.0.10.RELEASE/spring-aop-5.0.10.RELEASE.jar:/Users/superlee/.m2/repository/org/aspectj/aspectjweaver/1.8.13/aspectjweaver-1.8.13.jar:/Users/superlee/.m2/repository/org/springframework/retry/spring-retry/1.2.2.RELEASE/spring-retry-1.2.2.RELEASE.jar:/Users/superlee/.m2/repository/org/springframework/spring-core/5.0.10.RELEASE/spring-core-5.0.10.RELEASE.jar:/Users/superlee/.m2/repository/org/springframework/spring-jcl/5.0.10.RELEASE/spring-jcl-5.0.10.RELEASE.jar:/Users/superlee/.m2/repository/com/fasterxml/jackson/dataformat/jackson-dataformat-yaml/2.8.3/jackson-dataformat-yaml-2.8.3.jar:/Users/superlee/.m2/repository/com/fasterxml/jackson/core/jackson-core/2.9.7/jackson-core-2.9.7.jar:/Users/superlee/.m2/repository/org/yaml/snakeyaml/1.19/snakeyaml-1.19.jar:/Users/superlee/.m2/repository/io/codearte/props2yaml/props2yaml/0.5/props2yaml-0.5.jar:/Users/superlee/.m2/repository/org/slf4j/slf4j-api/1.7.25/slf4j-api-1.7.25.jar:/Users/superlee/.m2/repository/mysql/mysql-connector-java/5.1.47/mysql-connector-java-5.1.47.jar:/Users/superlee/.m2/repository/io/choerodon/choerodon-starter-metric/0.11.2.RELEASE/choerodon-starter-metric-0.11.2.RELEASE.jar:/Users/superlee/.m2/repository/io/micrometer/micrometer-registry-prometheus/1.0.7/micrometer-registry-prometheus-1.0.7.jar:/Users/superlee/.m2/repository/io/prometheus/simpleclient_common/0.4.0/simpleclient_common-0.4.0.jar:/Users/superlee/.m2/repository/io/prometheus/simpleclient/0.4.0/simpleclient-0.4.0.jar:/Users/superlee/.m2/repository/org/springframework/boot/spring-boot-autoconfigure/2.0.6.RELEASE/spring-boot-autoconfigure-2.0.6.RELEASE.jar:/Users/superlee/.m2/repository/commons-io/commons-io/2.4/commons-io-2.4.jar:/Users/superlee/.m2/repository/org/springframework/boot/spring-boot-starter-actuator/2.0.6.RELEASE/spring-boot-starter-actuator-2.0.6.RELEASE.jar:/Users/superlee/.m2/repository/org/springframework/boot/spring-boot-actuator-autoconfigure/2.0.6.RELEASE/spring-boot-actuator-autoconfigure-2.0.6.RELEASE.jar:/Users/superlee/.m2/repository/org/springframework/boot/spring-boot-actuator/2.0.6.RELEASE/spring-boot-actuator-2.0.6.RELEASE.jar:/Users/superlee/.m2/repository/io/micrometer/micrometer-core/1.0.7/micrometer-core-1.0.7.jar:/Users/superlee/.m2/repository/org/hdrhistogram/HdrHistogram/2.1.10/HdrHistogram-2.1.10.jar:/Users/superlee/.m2/repository/org/latencyutils/LatencyUtils/2.0.3/LatencyUtils-2.0.3.jar:/Applications/IntelliJ IDEA.app/Contents/lib/idea_rt.jar:/Users/superlee/Library/Caches/IntelliJIdea2018.3/captureAgent/debugger-agent.jar\"},\"user.name\":{\"value\":\"superlee\"},\"com.sun.management.jmxremote\":{\"value\":\"\"},\"java.vm.specification.version\":{\"value\":\"1.8\"},\"sun.java.command\":{\"value\":\"******\"},\"java.home\":{\"value\":\"/Library/Java/JavaVirtualMachines/jdk1.8.0_192.jdk/Contents/Home/jre\"},\"sun.arch.data.model\":{\"value\":\"64\"},\"user.language\":{\"value\":\"zh\"},\"java.specification.vendor\":{\"value\":\"Oracle Corporation\"},\"rebel.env.ide.plugin.version\":{\"value\":\"2018.2.6\"},\"awt.toolkit\":{\"value\":\"sun.lwawt.macosx.LWCToolkit\"},\"java.vm.info\":{\"value\":\"mixed mode\"},\"java.version\":{\"value\":\"1.8.0_192\"},\"java.ext.dirs\":{\"value\":\"/Users/superlee/Library/Java/Extensions:/Library/Java/JavaVirtualMachines/jdk1.8.0_192.jdk/Contents/Home/jre/lib/ext:/Library/Java/Extensions:/Network/Library/Java/Extensions:/System/Library/Java/Extensions:/usr/lib/java\"},\"sun.boot.class.path\":{\"value\":\"/Library/Java/JavaVirtualMachines/jdk1.8.0_192.jdk/Contents/Home/jre/lib/resources.jar:/Library/Java/JavaVirtualMachines/jdk1.8.0_192.jdk/Contents/Home/jre/lib/rt.jar:/Library/Java/JavaVirtualMachines/jdk1.8.0_192.jdk/Contents/Home/jre/lib/sunrsasign.jar:/Library/Java/JavaVirtualMachines/jdk1.8.0_192.jdk/Contents/Home/jre/lib/jsse.jar:/Library/Java/JavaVirtualMachines/jdk1.8.0_192.jdk/Contents/Home/jre/lib/jce.jar:/Library/Java/JavaVirtualMachines/jdk1.8.0_192.jdk/Contents/Home/jre/lib/charsets.jar:/Library/Java/JavaVirtualMachines/jdk1.8.0_192.jdk/Contents/Home/jre/lib/jfr.jar:/Library/Java/JavaVirtualMachines/jdk1.8.0_192.jdk/Contents/Home/jre/classes:/var/folders/sg/96g3db8s5rs2hxd1g3b4wt080000gn/T/jrebel-JRebel-201902130820-griffin/jrebel.jar\"},\"java.awt.headless\":{\"value\":\"true\"},\"java.vendor\":{\"value\":\"Oracle Corporation\"},\"com.zaxxer.hikari.pool_number\":{\"value\":\"1\"},\"spring.application.admin.enabled\":{\"value\":\"true\"},\"file.separator\":{\"value\":\"/\"},\"java.vendor.url.bug\":{\"value\":\"http://bugreport.sun.com/bugreport/\"},\"sun.io.unicode.encoding\":{\"value\":\"UnicodeBig\"},\"sun.cpu.endian\":{\"value\":\"little\"},\"sun.cpu.isalist\":{\"value\":\"\"}}},{\"name\":\"systemEnvironment\",\"properties\":{\"PATH\":{\"value\":\"/usr/local/bin:/usr/bin:/bin:/usr/sbin:/sbin:/Users/superlee/go/bin\",\"origin\":\"System Environment Property \\\"PATH\\\"\"},\"SHELL\":{\"value\":\"/bin/zsh\",\"origin\":\"System Environment Property \\\"SHELL\\\"\"},\"PAGER\":{\"value\":\"less\",\"origin\":\"System Environment Property \\\"PAGER\\\"\"},\"LSCOLORS\":{\"value\":\"Gxfxcxdxbxegedabagacad\",\"origin\":\"System Environment Property \\\"LSCOLORS\\\"\"},\"OLDPWD\":{\"value\":\"/Applications/IntelliJ IDEA.app/Contents/bin\",\"origin\":\"System Environment Property \\\"OLDPWD\\\"\"},\"GOPATH\":{\"value\":\"/Users/superlee/go\",\"origin\":\"System Environment Property \\\"GOPATH\\\"\"},\"USER\":{\"value\":\"superlee\",\"origin\":\"System Environment Property \\\"USER\\\"\"},\"VERSIONER_PYTHON_PREFER_32_BIT\":{\"value\":\"no\",\"origin\":\"System Environment Property \\\"VERSIONER_PYTHON_PREFER_32_BIT\\\"\"},\"ZSH\":{\"value\":\"/Users/superlee/.oh-my-zsh\",\"origin\":\"System Environment Property \\\"ZSH\\\"\"},\"TMPDIR\":{\"value\":\"/var/folders/sg/96g3db8s5rs2hxd1g3b4wt080000gn/T/\",\"origin\":\"System Environment Property \\\"TMPDIR\\\"\"},\"SSH_AUTH_SOCK\":{\"value\":\"/private/tmp/com.apple.launchd.285K7i9Vf8/Listeners\",\"origin\":\"System Environment Property \\\"SSH_AUTH_SOCK\\\"\"},\"GOBIN\":{\"value\":\"/Users/superlee/go/bin\",\"origin\":\"System Environment Property \\\"GOBIN\\\"\"},\"XPC_FLAGS\":{\"value\":\"0x0\",\"origin\":\"System Environment Property \\\"XPC_FLAGS\\\"\"},\"VERSIONER_PYTHON_VERSION\":{\"value\":\"2.7\",\"origin\":\"System Environment Property \\\"VERSIONER_PYTHON_VERSION\\\"\"},\"__CF_USER_TEXT_ENCODING\":{\"value\":\"0x1F5:0x19:0x34\",\"origin\":\"System Environment Property \\\"__CF_USER_TEXT_ENCODING\\\"\"},\"Apple_PubSub_Socket_Render\":{\"value\":\"/private/tmp/com.apple.launchd.8ra2eCJIbv/Render\",\"origin\":\"System Environment Property \\\"Apple_PubSub_Socket_Render\\\"\"},\"LOGNAME\":{\"value\":\"superlee\",\"origin\":\"System Environment Property \\\"LOGNAME\\\"\"},\"LESS\":{\"value\":\"-R\",\"origin\":\"System Environment Property \\\"LESS\\\"\"},\"LC_CTYPE\":{\"value\":\"\",\"origin\":\"System Environment Property \\\"LC_CTYPE\\\"\"},\"PWD\":{\"value\":\"/Users/superlee/Documents/idea_work/framework-group/manager-service\",\"origin\":\"System Environment Property \\\"PWD\\\"\"},\"XPC_SERVICE_NAME\":{\"value\":\"com.jetbrains.intellij.24248\",\"origin\":\"System Environment Property \\\"XPC_SERVICE_NAME\\\"\"},\"HOME\":{\"value\":\"/Users/superlee\",\"origin\":\"System Environment Property \\\"HOME\\\"\"}}},{\"name\":\"applicationConfig: [classpath:/application-default.yml]\",\"properties\":{\"spring.datasource.url\":{\"value\":\"jdbc:mysql://localhost:3306/manager_service?useUnicode=true&characterEncoding=utf-8&useSSL=false\",\"origin\":\"class path resource [application-default.yml]:4:10\"},\"spring.datasource.username\":{\"value\":\"root\",\"origin\":\"class path resource [application-default.yml]:5:15\"},\"spring.datasource.password\":{\"value\":\"******\",\"origin\":\"class path resource [application-default.yml]:6:15\"},\"spring.redis.host\":{\"value\":\"localhost\",\"origin\":\"class path resource [application-default.yml]:8:11\"},\"spring.redis.port\":{\"value\":6379,\"origin\":\"class path resource [application-default.yml]:9:11\"},\"spring.redis.database\":{\"value\":4,\"origin\":\"class path resource [application-default.yml]:11:15\"},\"mybatis.mapperLocations\":{\"value\":\"classpath*:/mapper/*.xml\",\"origin\":\"class path resource [application-default.yml]:13:20\"},\"mybatis.configuration.mapUnderscoreToCamelCase\":{\"value\":true,\"origin\":\"class path resource [application-default.yml]:15:31\"},\"eureka.instance.preferIpAddress\":{\"value\":true,\"origin\":\"class path resource [application-default.yml]:18:22\"},\"eureka.instance.leaseRenewalIntervalInSeconds\":{\"value\":1,\"origin\":\"class path resource [application-default.yml]:19:36\"},\"eureka.instance.leaseExpirationDurationInSeconds\":{\"value\":3,\"origin\":\"class path resource [application-default.yml]:20:39\"},\"eureka.client.serviceUrl.defaultZone\":{\"value\":\"http://localhost:8000/eureka/\",\"origin\":\"class path resource [application-default.yml]:23:20\"},\"choerodon.eureka.event.max-cache-size\":{\"value\":300,\"origin\":\"class path resource [application-default.yml]:27:23\"},\"choerodon.eureka.event.retry-time\":{\"value\":5,\"origin\":\"class path resource [application-default.yml]:28:19\"},\"choerodon.eureka.event.retry-interval\":{\"value\":3,\"origin\":\"class path resource [application-default.yml]:29:23\"},\"choerodon.eureka.event.skip-services\":{\"value\":\"config**, **register-server, **gateway**, zipkin**, hystrix**, oauth**\",\"origin\":\"class path resource [application-default.yml]:30:22\"},\"choerodon.swagger.client\":{\"value\":\"client\",\"origin\":\"class path resource [application-default.yml]:32:13\"},\"choerodon.swagger.oauth-url\":{\"value\":\"http://localhost:8080/oauth/oauth/authorize\",\"origin\":\"class path resource [application-default.yml]:33:16\"},\"choerodon.gateway.domain\":{\"value\":\"127.0.0.1:8080\",\"origin\":\"class path resource [application-default.yml]:35:13\"},\"choerodon.gateway.names\":{\"value\":\"api-gateway, gateway-helper\",\"origin\":\"class path resource [application-default.yml]:36:12\"},\"choerodon.register.executetTime\":{\"value\":100,\"origin\":\"class path resource [application-default.yml]:38:19\"},\"choerodon.profiles.active\":{\"value\":\"sit\",\"origin\":\"class path resource [application-default.yml]:40:13\"}}},{\"name\":\"applicationConfig: [classpath:/application.yml]\",\"properties\":{\"spring.datasource.url\":{\"value\":\"jdbc:mysql://localhost/manager_service?useUnicode=true&characterEncoding=utf-8&useSSL=false&useInformationSchema=true&remarks=true\",\"origin\":\"class path resource [application.yml]:3:10\"},\"spring.datasource.username\":{\"value\":\"choerodon\",\"origin\":\"class path resource [application.yml]:4:15\"},\"spring.datasource.password\":{\"value\":\"******\",\"origin\":\"class path resource [application.yml]:5:15\"},\"spring.redis.host\":{\"value\":\"localhost\",\"origin\":\"class path resource [application.yml]:7:11\"},\"spring.redis.port\":{\"value\":6379,\"origin\":\"class path resource [application.yml]:8:11\"},\"spring.redis.database\":{\"value\":4,\"origin\":\"class path resource [application.yml]:10:15\"},\"mybatis.mapperLocations\":{\"value\":\"classpath*:/mapper/*.xml\",\"origin\":\"class path resource [application.yml]:12:20\"},\"mybatis.configuration.mapUnderscoreToCamelCase\":{\"value\":true,\"origin\":\"class path resource [application.yml]:14:31\"},\"eureka.instance.preferIpAddress\":{\"value\":true,\"origin\":\"class path resource [application.yml]:17:22\"},\"eureka.instance.leaseRenewalIntervalInSeconds\":{\"value\":1,\"origin\":\"class path resource [application.yml]:18:36\"},\"eureka.instance.leaseExpirationDurationInSeconds\":{\"value\":3,\"origin\":\"class path resource [application.yml]:19:39\"},\"eureka.client.serviceUrl.defaultZone\":{\"value\":\"http://localhost:8000/eureka/\",\"origin\":\"class path resource [application.yml]:22:20\"},\"choerodon.eureka.event.max-cache-size\":{\"value\":300,\"origin\":\"class path resource [application.yml]:26:23\"},\"choerodon.eureka.event.retry-time\":{\"value\":5,\"origin\":\"class path resource [application.yml]:27:19\"},\"choerodon.eureka.event.retry-interval\":{\"value\":3,\"origin\":\"class path resource [application.yml]:28:23\"},\"choerodon.eureka.event.skip-services\":{\"value\":\"config**, **register-server, **gateway**, zipkin**, hystrix**, oauth**\",\"origin\":\"class path resource [application.yml]:29:22\"},\"choerodon.swagger.client\":{\"value\":\"client\",\"origin\":\"class path resource [application.yml]:31:13\"},\"choerodon.swagger.oauth-url\":{\"value\":\"http://localhost:8080/oauth/oauth/authorize\",\"origin\":\"class path resource [application.yml]:32:16\"},\"choerodon.gateway.domain\":{\"value\":\"127.0.0.1:8080\",\"origin\":\"class path resource [application.yml]:34:13\"},\"choerodon.gateway.names\":{\"value\":\"api-gateway, gateway-helper\",\"origin\":\"class path resource [application.yml]:35:12\"},\"choerodon.register.executetTime\":{\"value\":100,\"origin\":\"class path resource [application.yml]:37:19\"},\"choerodon.profiles.active\":{\"value\":\"sit\",\"origin\":\"class path resource [application.yml]:39:13\"}}},{\"name\":\"springCloudClientHostInfo\",\"properties\":{\"spring.cloud.client.hostname\":{\"value\":\"10.211.108.214\"},\"spring.cloud.client.ip-address\":{\"value\":\"10.211.108.214\"}}},{\"name\":\"applicationConfig: [classpath:/bootstrap.yml]\",\"properties\":{\"server.port\":{\"value\":8963,\"origin\":\"class path resource [bootstrap.yml]:2:9\"},\"spring.application.name\":{\"value\":\"manager-service\",\"origin\":\"class path resource [bootstrap.yml]:5:11\"},\"spring.mvc.static-path-pattern\":{\"value\":\"/**\",\"origin\":\"class path resource [bootstrap.yml]:7:26\"},\"spring.resources.static-locations\":{\"value\":\"classpath:/static,classpath:/public,classpath:/resources,classpath:/META-INF/resources,file:/dist\",\"origin\":\"class path resource [bootstrap.yml]:9:23\"},\"management.server.port\":{\"value\":8964,\"origin\":\"class path resource [bootstrap.yml]:12:11\"},\"management.endpoints.web.exposure.include\":{\"value\":\"*\",\"origin\":\"class path resource [bootstrap.yml]:16:18\"},\"management.endpoints.health.show-details\":{\"value\":\"ALWAYS\",\"origin\":\"class path resource [bootstrap.yml]:18:21\"},\"feign.hystrix.enabled\":{\"value\":false,\"origin\":\"class path resource [bootstrap.yml]:21:14\"}}},{\"name\":\"class path resource [asgard-client-hystrix-feign-config.properties]\",\"properties\":{\"feign.client.config.asgard-service.readTimeout\":{\"value\":\"100000\"},\"feign.client.config.asgard-service.connectTimeout\":{\"value\":\"100000\"}}},{\"name\":\"class path resource [default-choerodon-mybatis-config.properties]\",\"properties\":{\"mapper.enableMethodAnnotation\":{\"value\":\"false\"},\"mybatis.mapper-locations\":{\"value\":\"classpath*:/**/*Mapper.xml\"},\"mybatis.configuration.jdbcTypeForNull\":{\"value\":\"NULL\"},\"mybatis.configuration.map-underscore-to-camel-case\":{\"value\":\"true\"},\"pagehelper.pageSizeZero\":{\"value\":\"true\"},\"mapper.resolveClass\":{\"value\":\"io.choerodon.mybatis.mapperhelper.CustomEntityResolve\"}}},{\"name\":\"defaultProperties\",\"properties\":{}}]}";
        def response = new ResponseEntity<String>(body, HttpStatus.OK)


        and: 'mock'
        restTemplate.getForEntity(_, _) >> { return response }

        when: '根据instanceId查询Instance'
        mockDiscoveryClient.getServices() >> { return serviceList }
        mockDiscoveryClient.getInstances(_) >> { return serviceInstanceList }
        instanceService.query(instanceId)

        then: '分析结果'
        noExceptionThrown()
    }

    def "Update"() {
        given: '准备参数'
        def instanceId = 'test_server:test_ip:test_port'
        def wrongInstanceId = 'test_server:wrong'
        def configId = 1L
        def configVersion = new ArrayList<String>()
        configVersion.add("")

        when: '更新实例-badParameter'
        instanceService.update(wrongInstanceId, configId)
        then: '结果分析'
        def badParameter = thrown(CommonException)
        badParameter.message == "error.instance.updateConfig.badParameter"

        when: '更新实例'
        mockConfigMapper.selectConfigVersionById(_) >> { return configVersion }
        mockConfigServerClient.refresh(_) >> { return "" }
        instanceService.update(instanceId, configId)
        then: '结果分析'
        noExceptionThrown()
    }

    def "ListByOptions"() {
        given: '准备参数'
        def service = "test_service"
        def map = new HashMap<String, Object>()

        def serviceList = new ArrayList<String>()
        serviceList.add(service)

        def instanceInfo = new InstanceInfo(appName: "go-register-server", instanceId: "test_ip:test_server:test_port", healthCheckUrl: "http://111.111.11.111:1111/")
        def instanceInfo1 = new InstanceInfo(appName: "manager-server", instanceId: "test_ip:test_server:test_port", healthCheckUrl: "http://111.111.11.111:1111/")
        def leaseInfo = new LeaseInfo(registrationTimestamp: 1L)
        instanceInfo.setLeaseInfo(leaseInfo)
        instanceInfo1.setLeaseInfo(leaseInfo)
        def serviceInstance = new EurekaDiscoveryClient.EurekaServiceInstance(instanceInfo)
        def serviceInstance1 = new EurekaDiscoveryClient.EurekaServiceInstance(instanceInfo1)
        def serviceInstanceList = new ArrayList<ServiceInstance>()
        serviceInstanceList.add(serviceInstance)
        serviceInstanceList.add(serviceInstance1)

        and: "构造pageRequest"
        def order = new Sort.Order("id")
        Sort sort = new Sort(order)
        PageRequest pageRequest = new PageRequest(1, 20, sort) {
            @Override
            int getPageNumber() {
                return 0
            }

            @Override
            int getPageSize() {
                return 0
            }

            @Override
            long getOffset() {
                return 0
            }

            @Override
            Sort getSort() {
                return null
            }

            @Override
            PageRequest next() {
                return null
            }

            @Override
            PageRequest previousOrFirst() {
                return null
            }

            @Override
            PageRequest first() {
                return null
            }

            @Override
            boolean hasPrevious() {
                return false
            }
        }

        and: 'mock'
        mockDiscoveryClient.getServices() >> { return serviceList }
        mockDiscoveryClient.getInstances(_) >> { return serviceInstanceList }

        when: '查询实例列表'
        instanceService.listByOptions(service, map, pageRequest)
        then: '结果分析'
        noExceptionThrown()

        when: '查询实例列表'
        instanceService.listByOptions("", map, pageRequest)
        then: '结果分析'
        noExceptionThrown()
    }
}
