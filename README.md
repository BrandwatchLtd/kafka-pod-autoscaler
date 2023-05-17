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
  replicaConstraints:
    # Any of (all apply their constraints)
    - type: minMax
      metadata:
        minReplicas: '1'
        maxReplicas: '8'
    - type: kafkaPartitionFit
      metadata:
        bootstrapServers: <server list>
        consumerGroup: <consumer group>>
        topicName: <topic name>
  triggers:
    # Any of (max taken)
    - type: static # for testing
      metadata:
        replicas: '2'
    - type: cpu
      metadata:
        threshold: '50%'
    - type: prometheus
      metadata:
        serverAddress: addr 
        query: 'query'
        type: [Average/Max]
        threshold: '100'
    - type: kafka
      metadata:
        lagThreshold: '10000'
```
