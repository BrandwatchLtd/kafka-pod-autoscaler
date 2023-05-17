# KafkaPodAutoscaler

An implementation of an Autoscaler specific to pods which run Kafka workloads. Kafka topics have a preset number of partitions, and consuming applications work best when all replicas consume the same number of partitions.

For example, for a topic with 16 partitions the 'ideal' scaling for the number of replicas goes 1, 2, 4, 8, 16. This operator implements an autoscaler that will only scale to appropriate values for the number of partitions in a given topic. It supports a number of sources for scaling metrics: cpu, prometheus metrics and (of course) kafka lag.

# The KafkaPodAutoscaler CRD API

```yaml
apiVersion: com.brandwatch/v1alpha1
kind: KafkaPodAutoscaler
metadata:
  name: myautoscaler
spec:
  scaleTargetRef:
    apiVersion: apps/v1
    kind: Deployment
    name: mydeployment
  bootstrapServers: <server list>
  topicName: <topic name>
  triggers:
    - type: static # for testing
      metadata:
        replicas: 2
    - type: cpu
      metadata:
        threshold: 
    - type: prometheus
      metadata:
        serverAddress: 
        query: 
        type: [Average/Max]
        threshold: 
    - type: kafka
      metadata:
        lagThreshold: 
```
