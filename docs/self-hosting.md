# Self-Hosting feedless

Simplest `feedless` setup is using [docker](https://docs.docker.com/engine/install/).

## Docker Images
The relevant docker images for self hosting are: 
- `damoeb/feedless:agent`: Agent, that connects to the server using websockets, therefore can run anywhere.
- `damoeb/feedless:aio`: All-In-One image, that contains the core and the web app
- `damoeb/feedless:aio-chromium`: Like `damoeb/feedless:aio` but with agent (chromium, puppeteer)

## Using docker-compose

### Preparation
1) Clone repository
```
shell
git clone git@github.com:damoeb/feedless.git
cd feedless
```

### Start All-In-One image

```
shell
docker-compose up -d feedless-aio-chromium
```

If you don't need prerendering in a chromium there use this image.
```
shell
docker-compose up -d feedless-aio
```

## Using docker

### Preparation
1) Prepare environment flags
```shell
mkdir feedless
cd feedless
echo 'POSTGRES_USER=postgres
POSTGRES_USER=feedless
POSTGRES_PASSWORD=admin
APP_DATABASE_URL=jdbc:postgresql://postgres-db:5432/${POSTGRES_DB}
' > feedless.env
```

2) Create a network
```shell
docker network create -d bridge feedless-net
```

3) Start database
```shell
docker run --env-file=feedless.env --network=feedless-net --hostname=postgres -d postgres:15
```

### Start All-In-One Image

4) The minimal setup uses an image without headless chrome, so you will not be able to render Single-Page-Applications.
```shell
docker run --env-file=feedless.env --network=feedless-net -it damoeb/feedless:aio
```

Alternatively if you want JavaScript support use the `feedless:aio-chromium` image. This image includes a chrome browser for headless rendering.
```shell
docker run --env-file=feedless.env --network=feedless-net damoeb/feedless:aio-chromium
```

5) Check the logs. The all-in-one images use by default a single-tenant [authentication strategy](./authentication.md) that can be changed using environment flags.
Then wait until you see the feedless banner
```shell
feedless-core_1   |               . .
feedless-core_1   |  ,-           | |
feedless-core_1   |  |  ,-. ,-. ,-| | ,-. ,-. ,-.
feedless-core_1   |  |- |-' |-' | | | |-' `-. `-.
feedless-core_1   |  |  `-' `-' `-' ' `-' `-' `-'
feedless-core_1   | -'
feedless-core_1   | 

```

6) Open UI in browser [http://localhost:8080](http://localhost:8080) and login using `admin@localhost` and `password`


## Build All-In-One Image
Building images from scratch can be done using 
```
shell
git clone git@github.com:damoeb/feedless.git
cd feedless
./gradlew publish
```
