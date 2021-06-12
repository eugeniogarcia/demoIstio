# Preparation

We install the sleep smaple included with Istio.

```sh
kubectl apply -f samples/sleep/sleep.yaml

export SOURCE_POD=$(kubectl get pod -l app=sleep -o jsonpath={.items..metadata.name})
```

# `global.outboundTrafficPolicy.mode`

Istio has an installation option, global.outboundTrafficPolicy.mode, that configures the sidecar handling of external services, that is, those services that are not defined in Istio’s internal service registry. If this option is set to ALLOW_ANY, the Istio proxy lets calls to unknown services pass through. If the option is set to REGISTRY_ONLY, then the Istio proxy blocks any host without an HTTP service or service entry defined within the mesh. ALLOW_ANY is the default value.

```sh
kubectl get configmap istio -n istio-system -o yaml | grep -o "mode: ALLOW_ANY"
```

The string mode: ALLOW_ANY should appear in the output if it is enabled.

This call should work, despite `edition.cnn.com` is not in Istio registry:

```sh
kubectl exec -it $SOURCE_POD -c sleep -- curl -sL -o /dev/null -D - http://edition.cnn.com/politics
```

Run the following command to change the global.outboundTrafficPolicy.mode option to REGISTRY_ONLY.

```sh
kubectl get configmap istio -n istio-system -o yaml | sed 's/mode: ALLOW_ANY/mode: REGISTRY_ONLY/g' | kubectl replace -n istio-system -f -
```

We make the call again:

```sh
kubectl exec -it $SOURCE_POD -c sleep -- curl -sL -o /dev/null -D - http://edition.cnn.com/politics

command terminated with exit code 35
```

It does not work. Now lets create an entry for this external service in the registry:

```sh
kubectl apply -f - <<EOF
apiVersion: networking.istio.io/v1alpha3
kind: ServiceEntry
metadata:
  name: cnn
spec:
  hosts:
  - edition.cnn.com
  ports:
  - number: 80
    name: http-port
    protocol: HTTP
  - number: 443
    name: https
    protocol: HTTPS
  resolution: DNS
EOF
```

Now we make the call again, and see that it works

```sh
kubectl exec -it $SOURCE_POD -c sleep -- curl -sL -o /dev/null -D - http://edition.cnn.com/politics
```

## Direct access to external services

We can configure Istio so that the calls to external services are not handled by the sidecars. We can configure Istio so that certain _target ips_ are not handled by the _sidecar_. In our case the ips would be the external ip of Azure.

```sh
istioctl manifest apply --set values.global.istioNamespace=istio-system \
    --set global.proxy.includeIPRanges="10.244.0.0/16\,10.240.0.0/16
```

# Direct all external calls to the Egress Service

## Preparation

Check if the Istio egress gateway is deployed:

```sh
kubectl get pod -l istio=egressgateway -n istio-system
```

If no pods are returned, deploy the Istio egress gateway by performing the following command:

```sh
istioctl manifest apply --set values.global.istioNamespace=istio-system \
    --set values.gateways.istio-ingressgateway.enabled=false \
    --set values.gateways.istio-egressgateway.enabled=true
```

## Introduction

We have an egress service in the `istio-system` ns:

```sh
kubectl get svc -n istio-system istio-egressgateway -o yaml 
```

returns:

```yaml
apiVersion: v1
kind: Service
metadata:
  annotations:
    kubectl.kubernetes.io/last-applied-configuration: |
      {"apiVersion":"v1","kind":"Service","metadata":{"annotations":{},"labels":{"app":"istio-egressgateway","istio":"egressgateway","operator.istio.io/component":"EgressGateway","operator.istio.io/managed":"Reconcile","operator.istio.io/version":"1.4.0","release":"istio"},"name":"istio-egressgateway","namespace":"istio-system"},"spec":{"ports":[{"name":"http2","port":80},{"name":"https","port":443},{"name":"tls","port":15443,"targetPort":15443}],"selector":{"app":"istio-egressgateway"},"type":"ClusterIP"}}
  creationTimestamp: "2020-01-04T23:24:49Z"
  labels:
    app: istio-egressgateway
    istio: egressgateway
    operator.istio.io/component: EgressGateway
    operator.istio.io/managed: Reconcile
    operator.istio.io/version: 1.4.0
    release: istio
  name: istio-egressgateway
  namespace: istio-system
  resourceVersion: "38110"
  selfLink: /api/v1/namespaces/istio-system/services/istio-egressgateway
  uid: 6703f380-2f49-11ea-821a-3eba27734fb4
spec:
  clusterIP: 10.0.209.159
  ports:
  - name: http2
    port: 80
    protocol: TCP
    targetPort: 80
  - name: https
    port: 443
    protocol: TCP
    targetPort: 443
  - name: tls
    port: 15443
    protocol: TCP
    targetPort: 15443
  selector:
    app: istio-egressgateway
  sessionAffinity: None
  type: ClusterIP
status:
  loadBalancer: {}
```

We can see that the service can be found at `istio-egressgateway.istio-system..svc.cluster.local`, and has the label `app: istio-egressgateway`. That will point the service to the egress pod.

```sh
kubectl get po -n istio-system -l app=istio-egressgateway -o jsonpath={.items..metadata.name}

istio-egressgateway-75cb89bd7f-2vlh9
```

## Setup

We create the following resources:

```sh
kubectl apply -f - <<EOF
apiVersion: networking.istio.io/v1alpha3
kind: Gateway
metadata:
  name: istio-egressgateway
spec:
  selector:
    istio: egressgateway
  servers:
  - port:
      number: 80
      name: https
      protocol: HTTPS
    hosts:
    - edition.cnn.com
    tls:
      mode: MUTUAL
      serverCertificate: /etc/certs/cert-chain.pem
      privateKey: /etc/certs/key.pem
      caCertificates: /etc/certs/root-cert.pem
---
apiVersion: networking.istio.io/v1alpha3
kind: DestinationRule
metadata:
  name: egressgateway-for-cnn
spec:
  host: istio-egressgateway.istio-system.svc.cluster.local
  subsets:
  - name: cnn
    trafficPolicy:
      loadBalancer:
        simple: ROUND_ROBIN
      portLevelSettings:
      - port:
          number: 80
        tls:
          mode: ISTIO_MUTUAL
          sni: edition.cnn.com
EOF
```

```sh
kubectl apply -f - <<EOF
apiVersion: networking.istio.io/v1alpha3
kind: VirtualService
metadata:
  name: direct-cnn-through-egress-gateway
spec:
  hosts:
  - edition.cnn.com
  gateways:
  - istio-egressgateway
  - mesh
  http:
  - match:
    - gateways:
      - mesh
      port: 80
    route:
    - destination:
        host: istio-egressgateway.istio-system.svc.cluster.local
        subset: cnn
        port:
          number: 80
      weight: 100
  - match:
    - gateways:
      - istio-egressgateway
      port: 80
    route:
    - destination:
        host: edition.cnn.com
        port:
          number: 80
      weight: 100
EOF
```

## Explanation

- Hacemos una llamada a __http://edition.cnn.com/politics__
- El sidecar la procesa porque si bien la politica es __REGISTRY_ONLY__, hemos creado en el registry una entrada para __edition.cnn.com__, con el recurso __Kind=ServiceEntry con nombre cnn__.
- Hay un __virtual service__ llamado __direct-cnn-through-egress-gateway__, que se activa para el __host edition.cnn.com, gateway mesh__. El virtual service envia la peticion al __host istio-egressgateway.istio-system.svc.cluster.local, subset cnn__
- Hay una __destinationrule__, que se activa para el __host istio-egressgateway.istio-system.svc.cluster.local__, y que para el __subset cnn__ habilita el TLS. Esto significa que la peticion se envia al servicio de egress, y que va con TLS

```yaml
tls:
  mode: ISTIO_MUTUAL
  sni: edition.cnn.com
```
- El servicio de egress recive la peticion y la envia al pod de egress. El pod de egress es un proxy de istio - como los sidecar -. En este punto se comprueba si hay algun recurso __gateway__ que aplique. El recurso __gateway con nombre istio-egressgateway__ aplica para el __host edition.cnn.com__
- Como cualquier proxy, a continuacion se comprueba si hay algun virtual service. Lo hay, el __virtual service__ llamado __direct-cnn-through-egress-gateway__, que se activa para el __host edition.cnn.com, gateway istio-egressgateway__. En este caso el virtual service __envia la peticion a edition.cnn.com__.
- No hay ninguna destinationrule para este destino, asi que la peticio sale sin mas hacia edition.cnn.com, __pero se hace desde el egress pod__.