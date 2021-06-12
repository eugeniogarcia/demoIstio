# Imagen

Crear la imagen:

```ps
docker build . -t pruebacontenedor.azurecr.io/frontend-app:1.0
```

```ps
docker login pruebacontenedor.azurecr.io

docker tag frontend-app:1.0 pruebacontenedor.azurecr.io/frontend-app:1.0

docker push pruebacontenedor.azurecr.io/frontend-app:1.0
```