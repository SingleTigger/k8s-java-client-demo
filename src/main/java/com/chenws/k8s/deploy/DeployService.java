package com.chenws.k8s.deploy;

import io.kubernetes.client.ApiClient;
import io.kubernetes.client.Configuration;
import io.kubernetes.client.apis.AppsV1Api;
import io.kubernetes.client.apis.CoreV1Api;
import io.kubernetes.client.apis.ExtensionsV1beta1Api;
import io.kubernetes.client.models.*;
import io.kubernetes.client.util.Config;
import io.kubernetes.client.util.Yaml;
import org.springframework.core.io.ClassPathResource;

import java.io.*;
import java.util.HashMap;
import java.util.Map;

/**
 * Created by chenws on 2020/5/20
 */
public class DeployService {

    private final static String DEFAULT_NAMESPACE = "default";

    private final static String CONFIG_JSON = "config.json";

    private final static String LABEL_KEY = "app";

    private final static String CONFIG_MAP_PREFIX = "configMap-";

    private final static String CONFIG_JSON_VALUE = "{}";

    /**
     * 1.load k8s config, all the configs come from the server where the k8s on.
     * 2.create deployment
     * 3.create service
     * 4.create ingress
     *
     * @throws Exception e
     */
    public void deploy() throws Exception {
        //load config
        String configPath = System.getProperty("user.dir") + File.separator + "kubeConfig" + File.separator;
        ApiClient apiClient = Config.fromConfig(new FileInputStream(configPath + "config"));
        Configuration.setDefaultApiClient(apiClient);

        //create deployment
        String deploymentName = createDeployment(apiClient, CONFIG_JSON_VALUE);
        //create service
        String serviceName = createService(deploymentName);
        //create ingress
        createIngress(apiClient, serviceName);
    }

    public String createDeployment(ApiClient apiClient, String configJsonValue) {
        try {
            //load the template and replace config
            ClassPathResource classPathResource = new ClassPathResource("deployment.yml");
            InputStream inputStream = classPathResource.getInputStream();
            Reader reader = new InputStreamReader(inputStream);
            //get the entity
            V1Deployment v1Deployment = (V1Deployment) Yaml.load(reader);
            reader.close();
            AppsV1Api apiInstance = new AppsV1Api(apiClient);

            String name = "deployment-name";
            //create config map
            String configMapName = createConfigMap(configJsonValue, name);

            //config the deployment
            v1Deployment.getMetadata().setName(name);
            v1Deployment.getMetadata().getLabels().put(LABEL_KEY, name);
            v1Deployment.getSpec().getSelector().getMatchLabels().put(LABEL_KEY, name);
            V1PodTemplateSpec template = v1Deployment.getSpec().getTemplate();
            template.getMetadata().getLabels().put(LABEL_KEY, name);

            V1Container v1Container = template.getSpec().getContainers().get(0);
            v1Container.setName(name);
            //your target image
            v1Container.setImage("test-mirror-name");

            //configMap
            V1Volume v1Volume = template.getSpec().getVolumes().get(0);
            V1ConfigMapVolumeSource configMap1 = v1Volume.getConfigMap();
            //configMap name
            configMap1.setName(configMapName);
            V1KeyToPath v1KeyToPath = configMap1.getItems().get(0);
            //configMap key
            v1KeyToPath.setKey(CONFIG_JSON);
            //config map's file name
            v1KeyToPath.setPath(CONFIG_JSON);
            //create
            apiInstance.createNamespacedDeployment(DEFAULT_NAMESPACE, v1Deployment, null, null, null);
            return name;
        } catch (Exception e) {
            e.printStackTrace();
            return "";
        }

    }

    public String createService(String deploymentName) {
        try {
            ClassPathResource classPathResource = new ClassPathResource("service.yml");
            InputStream inputStream = classPathResource.getInputStream();
            Reader reader = new InputStreamReader(inputStream);
            V1Service v1Service = (V1Service) Yaml.load(reader);
            reader.close();
            String serviceName = "service-name";
            v1Service.getMetadata().setName(serviceName);
            v1Service.getMetadata().getLabels().put(LABEL_KEY, serviceName);
            //deployment name
            v1Service.getSpec().getSelector().put(LABEL_KEY, deploymentName);

            CoreV1Api api = new CoreV1Api();
            //create
            api.createNamespacedService(DEFAULT_NAMESPACE, v1Service, null, null, null);
            return serviceName;
        } catch (Exception e) {
            e.printStackTrace();
            return "";
        }
    }

    private static String createConfigMap(String configJsonValue, String name) {
        try {
            CoreV1Api api = new CoreV1Api();
            Map<String, String> data = new HashMap<>();
            String configMapName = CONFIG_MAP_PREFIX + name;
            //key: file name, value: the text of file
            data.put(CONFIG_JSON, configJsonValue);
            V1ConfigMap configMap = (new V1ConfigMapBuilder())
                    .withApiVersion("v1")
                    .withKind("ConfigMap")
                    .withNewMetadata().withName(configMapName).withNamespace(DEFAULT_NAMESPACE)
                    .endMetadata()
                    .withData(data)
                    .build();
            //delete old config map
            api.deleteNamespacedConfigMap(configMapName, DEFAULT_NAMESPACE, null, null, null, null, true, null);
            //create config map
            api.createNamespacedConfigMap(DEFAULT_NAMESPACE, configMap, null, null, null);
            return configMapName;
        } catch (Exception e) {
            e.printStackTrace();
            return "";
        }
    }

    private void createIngress(ApiClient apiClient, String serviceName) {
        try {
            ClassPathResource classPathResource = new ClassPathResource("ingress.yml");
            InputStream inputStream = classPathResource.getInputStream();
            Reader reader = new InputStreamReader(inputStream);
            V1beta1Ingress v1beta1Ingress = (V1beta1Ingress) Yaml.load(reader);
            reader.close();
            v1beta1Ingress.getMetadata().setName("ingress-service-deployment");
            V1beta1IngressRule v1beta1IngressRule = v1beta1Ingress.getSpec().getRules().get(0);
            V1beta1HTTPIngressRuleValue http = v1beta1IngressRule.getHttp();
            V1beta1HTTPIngressPath v1beta1HTTPIngressPath = http.getPaths().get(0);
            v1beta1HTTPIngressPath.setPath("/api/(/|$)(.*)");
            v1beta1HTTPIngressPath.getBackend().setServiceName(serviceName);
            ExtensionsV1beta1Api extensionsV1beta1Api = new ExtensionsV1beta1Api(apiClient);
            extensionsV1beta1Api.createNamespacedIngress(DEFAULT_NAMESPACE, v1beta1Ingress, null, null, null);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }


}
