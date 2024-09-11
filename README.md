# Komet With JPro

### Team Ownership - Product Owner
External Contributors.

## Getting Started
Follow the instructions below to set up the local environment for Komet:

1. Download and install Open JDK Java 21
2. Download and install Apache Maven 3.9 or greater
3. Prior to building Komet, there are additional repositories to clone and build. Please use
   the [`tinkar-core` README](https://github.com/ikmdev/tinkar-core/blob/main/README.md) file to build the `tinkar-core`
   project and its prerequisites before building `komet`.

## Building and Running Komet with JPro locally
Follow the steps below to build and run Komet with JPro on your local machine:
1. Clone the [ikm-jpro repository](https://github.com/ikmdev/ikm-jpro) from GitHub to your local machine
2. Change local directory to location to `ikm-jpro`
3. Enter the following command to build the application:
```bash
mvn clean install
```
4. Run the Komet application with JPro locally with the following command:
```bash
mvn -f application jpro:run
```
5. The default web browser should automatically open and you should see Komet application running, otherwise just
navigate to `http://localhost:8080` in your web browser.
6. To stop the application, press `Ctrl + C` in the terminal window where the application is running.
7. To run the application again, repeat step 4.
8. The application can also run as classic desktop JavaFX app with the following command:
```bash
mvn -f application javafx:run
```

## Running Komet with JPro in a Docker Container
Follow the steps below to run Komet with JPro in a Docker container:
1. Clone the [ikm-jpro repository](https://github.com/ikmdev/ikm-jpro) from GitHub to your local machine
2. Change local directory to location to `ikm-jpro`
3. Enter the following command to build the application:
```bash
mvn clean install
```
4. Create the application release zip for deployment with the following command:
```bash
mvn clean -f application jpro:release
```
The application release zip will be built and the output will be in the `application/target` directory, with the name
`application-jpro.zip`.
5. Transfer the `application-jpro.zip` file to the directory where you want to run the Docker container.
6. Extract the `application-jpro.zip` content and change the directory to the unzipped directory.
7. Run the application in a docker container:
  * **Option 1**: Build the Docker image and run the Docker container manually
    * Inside the unzipped directory, there is a `Dockerfile` file. Create a Docker image with the following command:
      ```bash
      docker build -t komet-jpro .
      ```
    * Run the Docker container with the following command:
      ```bash
      docker run -d -v ~/Solor:/root/Solor -p 8080:8080 komet-jpro
      ```
      Note: `-v ~/Solor:/root/Solor`: This option mounts a volume. It creates a mapping between a directory on the host
      machine and a directory inside the container.
      * **~/Solor**: This is the path to the directory on the host machine (or your local system). The `~` is a
      shorthand for the current user’s home directory. This path should be the directory where the dataset is located.
      * **/root/Solor**: This is the path inside the container where the host directory will be mounted.
      Anything in **~/Solor** on the host will be accessible inside the container at **/root/Solor**.
  * **Option 2**: Use Docker Compose to run the Docker container
    * Inside the unzipped directory, there is a `docker-compose.yml` file. You can use this file to run the Docker
    container with Docker Compose. To run the Docker container with Docker Compose, use the following command:
      ```bash
      docker-compose up -d
      ```
8. The application should be running in the Docker container. To access the application, open a web browser and navigate
to `http://localhost:8080` if you are running the Docker container locally. If you are running the Docker container on a
remote server, replace `localhost` with the IP address of the server.

Note: Komet requires sample data to operate with full functionality
y

## Issues and Contributions
Technical and non-technical issues can be reported to the [Issue Tracker](https://github.com/ikmdev/ikm-jpro/issues).

Contributions can be submitted via pull requests. Please check the [contribution guide](doc/how-to-contribute.md) for more details.

