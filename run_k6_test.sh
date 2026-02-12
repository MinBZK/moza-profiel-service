#!/bin/bash

# Script to run k6 load test on Kubernetes and retrieve results.

SCRIPT_NAME="example.js"
CONFIGMAP_NAME="k6-test-script"
JOB_NAME="k6-load-test"
HELPER_POD_NAME="k6-results-viewer"
PVC_NAME="k6-results-pvc"
LOCAL_RESULTS_DIR="./k6-results"

echo "1. Creating/Updating ConfigMap: $CONFIGMAP_NAME from $SCRIPT_NAME..."
kubectl delete configmap $CONFIGMAP_NAME --ignore-not-found
kubectl create configmap $CONFIGMAP_NAME --from-file=$SCRIPT_NAME

echo "2. Applying Job and PVC from job.yaml..."
# Ensure any old job is removed so it can be restarted
kubectl delete job $JOB_NAME --ignore-not-found
kubectl apply -f job.yaml

echo "3. Waiting for Job $JOB_NAME to finish..."
# Wait for the job to finish. We check for 'complete' or 'failed' conditions.
# Some environments might show 'Error' but the Job eventually marks itself as 'failed' 
# after the backoffLimit is reached.
until kubectl get job $JOB_NAME -o jsonpath='{.status.conditions[?(@.type=="Complete")].status}' | grep -q "True" || \
      kubectl get job $JOB_NAME -o jsonpath='{.status.conditions[?(@.type=="Failed")].status}' | grep -q "True"; do
    
    # Check if the pod has entered an Error state which might indicate it won't succeed
    # We look for phase 'Failed' or container state 'terminated' with non-zero exit code or reason 'Error'
    POD_JSON=$(kubectl get pods --selector=job-name=$JOB_NAME -o json 2>/dev/null)
    # Check if any pod was found
    if [ "$(echo "$POD_JSON" | jq '.items | length')" -gt 0 ]; then
        POD_PHASE=$(echo "$POD_JSON" | jq -r '.items[0].status.phase // empty' 2>/dev/null)
        # Get details of the k6 container specifically
        K6_CONTAINER_STATE=$(echo "$POD_JSON" | jq -r '.items[0].status.containerStatuses[]? | select(.name=="k6") | .state' 2>/dev/null)
        EXIT_CODE=$(echo "$K6_CONTAINER_STATE" | jq -r '.terminated.exitCode // empty' 2>/dev/null)
        TERMINATED_REASON=$(echo "$K6_CONTAINER_STATE" | jq -r '.terminated.reason // empty' 2>/dev/null)

        if [ "$POD_PHASE" == "Succeeded" ]; then
            echo "Job pod succeeded. Proceeding to retrieve results..."
            break
        fi

        if [ "$POD_PHASE" == "Failed" ] || [ "$TERMINATED_REASON" == "Error" ] || [[ -n "$EXIT_CODE" && "$EXIT_CODE" != "0" ]]; then
            echo "Job pod finished with status: ${POD_PHASE:-Unknown} (Exit Code: ${EXIT_CODE:-None}, Reason: ${TERMINATED_REASON:-None}). Proceeding to retrieve results..."
            break
        fi
        
        echo "Job still running... (Pod Phase: ${POD_PHASE:-Pending/Starting})"
    else
        echo "Job still starting (no pods found yet)..."
    fi
    sleep 10
done

echo "4. Creating helper pod for results retrieval: $HELPER_POD_NAME..."
# Create a temporary pod to access the volume
kubectl run $HELPER_POD_NAME --image=busybox --restart=Never --overrides='
{
  "spec": {
    "volumes": [{
      "name": "results",
      "persistentVolumeClaim": { "claimName": "'"$PVC_NAME"'" }
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

echo "Waiting for helper pod $HELPER_POD_NAME to be ready..."
kubectl wait --for=condition=Ready pod/$HELPER_POD_NAME --timeout=60s

echo "5. Copying results to $LOCAL_RESULTS_DIR..."
mkdir -p $LOCAL_RESULTS_DIR
kubectl cp $HELPER_POD_NAME:/results $LOCAL_RESULTS_DIR

echo "6. Cleaning up..."
kubectl delete pod $HELPER_POD_NAME
# Optional: keep the job/configmap or delete them? Usually good to keep for logs until next run.

echo "Done! Results are available in $LOCAL_RESULTS_DIR"
