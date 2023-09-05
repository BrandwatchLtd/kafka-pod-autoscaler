# KafkaPodAutoscaler

An implementation of an Autoscaler specific to pods which run Kafka workloads. Kafka topics have a preset number of partitions, and consuming applications work best when all replicas consume the same number of partitions.

For example, for a topic with 16 partitions the 'ideal' scaling for the number of replicas goes 1, 2, 4, 8, 16. This 
operator implements an autoscaler that will only scale to appropriate values for the number of partitions in a given 
topic. It supports a number of sources for scaling metrics:
* [cpu](src/main/java/com/brandwatch/kafka_pod_autoscaler/triggers/CpuTriggerProcessor.java): traditional 
  cpu-metrics-based scaling, based on the last 5 minutes of readings
* [prometheus metrics](src/main/java/com/brandwatch/kafka_pod_autoscaler/triggers/PrometheusTriggerProcessor.java)
* [predicted kafka lag/throughput](src/main/java/com/brandwatch/kafka_pod_autoscaler/triggers/KafkaLagTriggerProcessor.
  java): a predictive mode where the load while lagged is used to calculate the ideal number of replicas for the 
  current throughput on the topic.  This is intended to ensure that even when not lagged the number of consumers is 
  scaled appropriately for the current load, so lag is less likely to occur. 

## The KafkaPodAutoscaler CRD API

```yaml
apiVersion: com.brandwatch/v1alpha1
kind: KafkaPodAutoscaler
metadata:
  name: myautoscaler
spec:
  dryRun: false # set true to see what the autoscaler _would_ do
  cooloffSeconds: 300
  maxScaleIncrements: 1 # how many increments to scale by, increment of 1 will allow scaling from 1->2, 2->4, 4->8 
                        #2 will allow 1->4 but not 1->8 
  minReplicas: 1        # optional
  maxReplicas: 16       # optional: partition count will be used if missing 
  scaleTargetRef:
    apiVersion: apps/v1
    kind: Deployment
    name: mydeployment
  bootstrapServers: <server list>
  topicName: <topic name>
  triggers:
    - type: static # for testing
      metadata:
        replicas: [Required]
    - type: cpu
      metadata:
        threshold: [Required]
    - type: prometheus
      metadata:
        serverAddress: 
        query: 
        type: [Total/Absolute] # Should the query result be treated as:
                               # a total across all pods (divide per #pods to compare with threshold, or 
                               # an Absolute value directly comparable with threshold
        threshold: 
    - type: kafka
      metadata:
        consumerGroupId: [Required]
        threshold: [Required] 
        sla: P10M
```


## Contributing

This repository uses [Conventional Commits](https://www.conventionalcommits.org/en/v1.0.0/) to generate a changelog and semver-based versions for every push to the `main` branch.

Open a PR with your proposed changes, and your commits will be validated against the conventions
