# k6 Load Testing on Kubernetes

This guide explains how to run the k6 load test script (`example.js`) using the provided Kubernetes Job configuration (`job.yaml`).

## Overview
The load test is configured to:
- Targets **100,000 requests** within a **5-minute** window.
- Maintains a constant arrival rate of ~334 requests per second.
- Monitors response times to ensure 100% of requests are under **1 second**.
- Generates JSON, JUnit XML, and **HTML** reports.

### External Visualization Tools
If you want to visualize the `summary.json` file specifically without re-running the test:
- **k6-visualizer (Web)**: You can upload your `summary.json` to [k6-visualizer.com](https://k6-visualizer.com/) to generate graphs instantly.
- **Grafana**: For long-term monitoring, k6 can stream metrics directly to InfluxDB or Prometheus, which can then be visualized in Grafana.

## Visualization

The easiest way to visualize the results is using the generated HTML report:
1. Follow the steps in **Retrieve the Results**.
2. Open `k6-results/summary.html` in your web browser.

This report provides a graphical overview of:
- Request rates and duration.
- Success/failure rates.
- Threshold status.
- Detailed metrics per endpoint.

## Prerequisites
- A running Kubernetes cluster.
- `kubectl` configured to access your cluster.
- A PersistentVolume (PV) provider available in your cluster (for the PVC).

## Step-by-Step Instructions

### 1. Create the ConfigMap
The Kubernetes Job mounts the k6 script from a ConfigMap. Create it using your local `example.js` file:

```bash
kubectl create configmap k6-test-script --from-file=example.js
```

### 2. Create the PersistentVolumeClaim (PVC)
The Job expects a PVC named `k6-results-pvc` to store the output reports. If you don't have one yet, you can create a simple one:

```yaml
# pvc.yaml
apiVersion: v1
kind: PersistentVolumeClaim
metadata:
  name: k6-results-pvc
spec:
  accessModes:
    - ReadWriteOnce
  resources:
    requests:
      storage: 1Gi
```

Apply it:
```bash
kubectl apply -f pvc.yaml
```

### 3. Run the Load Test Job
Before applying, ensure the `BASE_URL` in `job.yaml` points to your service's internal cluster URL (e.g., `http://profiel-service:8080/api/profielservice/v1`).

```bash
kubectl apply -f job.yaml
```

### 4. Monitor the Test
You can follow the logs to see the real-time progress:

```bash
kubectl logs -f job/k6-load-test
```

### 5. Retrieve the Results
Once the Job is completed, the reports are stored in the `/results` directory on the PVC. 

Since `kubectl cp` requires a running container and may fail if the Job pod has already exited (especially if it failed), the most reliable way to retrieve results is to use a temporary "helper" pod that mounts the same volume.

#### Option A: Using a helper pod (Recommended)
1. Create a temporary pod to access the volume:
```bash
kubectl run k6-results-viewer --image=busybox --restart=Never --overrides='
{
  "spec": {
    "volumes": [{
      "name": "results",
      "persistentVolumeClaim": { "claimName": "k6-results-pvc" }
    }],
    "containers": [{
      "name": "viewer",
      "image": "busybox",
      "command": ["sleep", "3600"],
      "volumeMounts": [{
        "mountPath": "/results",
        "name": "results"
      }]
    }]
  }
}'
```

2. Copy the results from the helper pod:
```bash
kubectl cp k6-results-viewer:/results ./k6-results
```

3. Delete the helper pod:
```bash
kubectl delete pod k6-results-viewer
```

#### Option B: Direct copy (May fail on completed pods)
If the pod is still available and your cluster supports it:
```bash
# Get the pod name
POD_NAME=$(kubectl get pods --selector=job-name=k6-load-test -o jsonpath='{.items[0].metadata.name}')

# Copy the results folder to your local machine
kubectl cp $POD_NAME:/results ./k6-results
```

### 6. Automated Script (Recommended)
Alternatively, you can use the provided script to automate the entire process (creating ConfigMap, applying Job, waiting for completion, and retrieving results):

```bash
chmod +x run_k6_test.sh
./run_k6_test.sh
```

## Configuration
- **Script**: Modify `example.js` to change the load profile, endpoints, or thresholds. Remember to recreate the ConfigMap after changes.
- **Environment Variables**: You can update the `BASE_URL` in `job.yaml` without changing the script.
- **Thresholds**: By default, the `job.yaml` now enforces thresholds. If any performance goal (like latency < 1s) is not met, the k6 process will exit with an error, causing the Kubernetes Job to be marked as `Failed`. You can still retrieve the reports from the PVC using the **Automated Script** or the **Helper Pod** method. If you want the Job to always finish with a `Succeeded` status regardless of performance, add the `--no-thresholds` flag back to the `command` in `job.yaml`.
- **Scaling**: If k6 needs more resources to hit the target throughput, adjust `preAllocatedVUs` and `maxVUs` in `example.js` and resource limits in `job.yaml`.

## Cleanup
To delete the job and start over:

```bash
kubectl delete job k6-load-test
# Optional: delete the configmap if you want to update the script
kubectl delete configmap k6-test-script
```
