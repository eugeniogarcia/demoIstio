# Requirements.txt

El archivo `requirements.txt` contiene la lista de dependencias de nuestra aplicación python. Podemos crear el archivo de dos formas:

- 1. Recuperando todas las dependencias del entorno de trabajo

```ps
pip freeze > requirements.txt
```

- 2. Recuperando las dependencias utilizadas en un directorio. Para ello primero tenemos que instalar la utilidad `pipreqs`:

```ps
pip install pipreqs
```

Para obtener las dependencias:

```ps
pipreqs .\istio\
```

# Imagen

Crear la imagen:

```ps
docker build . -t web-app:3.0 --build-arg ver=3.0
docker build . -t web-app:3.1 --build-arg ver=3.1
docker build . -t web-app:3.2 --build-arg ver=3.2
```

```ps
docker login pruebacontenedor.azurecr.io

docker tag web-app:3.0 pruebacontenedor.azurecr.io/web-app:3.0
docker tag web-app:3.1 pruebacontenedor.azurecr.io/web-app:3.1
docker tag web-app:3.2 pruebacontenedor.azurecr.io/web-app:3.2

docker push pruebacontenedor.azurecr.io/web-app:3.0
docker push pruebacontenedor.azurecr.io/web-app:3.1
docker push pruebacontenedor.azurecr.io/web-app:3.2
```

## Check Vulnerabilidades

```ps
docker scan web-app:1.0

Testing web-app:1.0...

Package manager:   deb
Project name:      docker-image|web-app
Docker image:      web-app:1.0
Platform:          linux/amd64

✓ Tested 93 dependencies for known vulnerabilities, no vulnerable paths found.

Note that we currently do not have vulnerability information for Ubuntu 21.10, which we detected in your image.

For more free scans that keep your images secure, sign up to Snyk at https://dockr.ly/3ePqVcp
```

# Private Registry

```ps
docker login pruebacontenedor.azurecr.io

docker tag web-app:1.0 pruebacontenedor.azurecr.io/web-app:1.0

docker push pruebacontenedor.azurecr.io/web-app:1.0
```

## Secret

```ps
kubectl create secret docker-registry milocalregistry --docker-server=pruebacontenedor.azurecr.io --docker-username=pruebacontenedor --docker-password=Pr0bDGtfdIKbWj+pGbGEsFpc8D/3enAH -n default
```

# Ejemplo

## Recuros Kubernetes

```ps
kubectl apply -f .\deployment.yml

kubectl apply -f .\deployment1.yml

kubectl apply -f .\deployment2.yml

kubectl apply -f .\service.yml
```

```ps
kubectl get svc

NAME          TYPE           CLUSTER-IP     EXTERNAL-IP    PORT(S)        AGE
details       ClusterIP      10.0.134.97    <none>         9080/TCP       5d
kubernetes    ClusterIP      10.0.0.1       <none>         443/TCP        5d3h
productpage   ClusterIP      10.0.134.138   <none>         9080/TCP       5d
ratings       ClusterIP      10.0.214.250   <none>         9080/TCP       5d
reviews       ClusterIP      10.0.5.213     <none>         9080/TCP       5d
webservice    LoadBalancer   10.0.28.19     20.76.184.72   80:32165/TCP   6h19m
```

## Recursos Istio

### Destination Rule

Creamos el destination rule:

```ps
kubectl apply -f .\detinationrule.yml
```

Creamos tres destinos, y demostramos las opciones para:
- Balancear la carga
- Definir un Circuit Breaker
- Crear un pool de conexiones
- Encriptar las comunicaciones

Definimos tres rutas para el host `webservice`, con el nombre `v1`, `v2` y `v3`, que apuntan a las tres deployments:  

```yml
apiVersion: networking.istio.io/v1alpha3
kind: DestinationRule
metadata:
  name: webapp-destination
spec:
  host: webservice
  subsets:
  - name: v0
    labels:
      version: "3.0"
```

### Virtual Service

#### Forward

Envia tadas las peticiones al mismo destino

```ps
kubectl apply -f .\forward.yml
```

Todas las respuestas vienen de la versión _v1_.

#### Rewrite

```ps
kubectl apply -f .\rewrite.yml
```

Comprueba que la petición se haga a una url con un determinado prefijo, reescribe la uri y continua la petición hacia la versión _v1_:

```ps
kubectl exec pod/frontend-deployment-c9c975b4-p8z2t -- wget -O - http://webservice/hello
```

#### Header

Comprueba las cabeceras de la petición, y dependiendo del contenido, dirige la petición a un sitio u a otro.

#### Weighted

Demuestra la distribución de las peticiones a diferentes versiónes según un peso de distribución:

```ps
kubectl apply -f .\weight.yml
```

En el 75% de los casos irá al _v0_, el 25% al _v1_.

#### AND y OR

Podemos enlazar condiciones para _matchear_ la petición:

```yaml
  - match:
    - headers:
        x-upgrade:
          exact: "TRUE"
    - queryParams:
        ver:
          exact: v1
      method:
        exact: GET
```

Si tenemos la cabecera _x-upgrade_ a TRUE O, en query params tenemos ver igual a v1 Y el http method es _GET_, se envia la ruta determinada.

### Gateway

Creamos un selfsigned certificate para encriptar la comunicación con el gateway:

```ps
openssl req -x509 -newkey rsa:4096 -keyout key.pem -out cert.pem -days 365

Generating a RSA private key
....................................................................++++
..............................++++
writing new private key to 'key.pem'
Enter PEM pass phrase:
Verifying - Enter PEM pass phrase:
-----
You are about to be asked to enter information that will be incorporated
into your certificate request.
What you are about to enter is what is called a Distinguished Name or a DN.
There are quite a few fields but you can leave some blank
For some fields there will be a default value,
If you enter '.', the field will be left blank.
-----
Country Name (2 letter code) [AU]:ES
State or Province Name (full name) [Some-State]:MA
Locality Name (eg, city) []:Torrelodones
Organization Name (eg, company) [Internet Widgits Pty Ltd]:MySelf
Organizational Unit Name (eg, section) []:Casa
Common Name (e.g. server FQDN or YOUR name) []:*.greetings.com
Email Address []:egsmartin@gmail.com
```

Finalmente creamos el certificado:

```ps
openssl rsa -in key.pem -out key2.pem
```

Finalmente creamos un secret con los certificados. Más que crear, actualizamos el que usa Istio para el gateway:

```ps
kubectl create -n istio-system secret tls istio-ingressgateway-certs --key key2.pem --cert cert.pem
```

Instalamos el gateway:

```ps
kubectl apply -f .\gateway.yml
```

Con esto aceptamos peticiones al host _*.greetings.com_:

```yml
- port:
    number: 80
    name: http
    protocol: HTTP
  hosts:
  - "*.greetings.com"
```

Necesitamos un servicio virtual que procese peticiones del gateway:

```ps
kubectl apply -f .\weight.yml
```

En el servicio virtual indicamos la mesh:

```yml
  gateways :
  - webapp-gateway
```

Por defecto sino indicamos ningún gateway, el valor que se asume es:

```yml
  gateways :
  - mesh
```

Es decir, se considera que el virtual service solo aplica a peticiones que llegen desde el interior de la mesh. 

Para hacer las llamadas desde el exterior usaremos el servicio de gateway de Istio. Veamos los datos de acceso del ingress del Istio:

```ps
kubectl get svc -n istio-system

NAME                   TYPE           CLUSTER-IP     EXTERNAL-IP    PORT
grafana                ClusterIP      10.0.77.130    <none>         3000/
istio-egressgateway    ClusterIP      10.0.62.120    <none>         80/TCP,443/
istio-ingressgateway   LoadBalancer   10.0.195.25    20.82.62.186   15021:31777/TCP,80:32227/TCP,443:32514/
istiod                 ClusterIP      10.0.37.21     <none>         15010/TCP,15012/TCP,443/TCP,15014/
jaeger-collector       ClusterIP      10.0.40.165    <none>         14268/TCP,14250/
kiali                  ClusterIP      10.0.76.244    <none>         20001/TCP,9090/
prometheus             ClusterIP      10.0.103.136   <none>         9090/
tracing                ClusterIP      10.0.203.254   <none>         80/
zipkin                 ClusterIP      10.0.119.120   <none>         9411/
```

Para probar el gateway, recordemos que Istio saca el nombre del host de la cabecera de la petición:

```
curl -v -HHost:webservice.greetings.com http://20.82.62.186/

*   Trying 20.82.62.186...
* TCP_NODELAY set
* Connected to 20.82.62.186 (20.82.62.186) port 80 (#0)
> GET / HTTP/1.1
> Host:webservice.greetings.com
> User-Agent: curl/7.58.0
> Accept: */*
>
< HTTP/1.1 200 OK
< content-type: text/html; charset=utf-8
< content-length: 62
< server: istio-envoy
< date: Sat, 12 Jun 2021 13:29:23 GMT
< x-envoy-upstream-service-time: 5
<
* Connection #0 to host 20.82.62.186 left intact
[3.0]Welcome user! current time is 2021-06-12 13:29:23.956053
```
