# Komet With JPro
## Getting Started
Follow the instructions below to set up the local environment for Komet:

1. Download and install Open JDK Java 21
2. Download and install Apache Maven 3.9 or greater
3. Prior to building Komet, there are additional repositories to clone and build. Please use
   the [`tinkar-core` README](https://github.com/ikmdev/tinkar-core/blob/main/README.md) file to build the `tinkar-core`
   project and its prerequisites before building `komet`.

## Building and Running Komet with JPro
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
8. To build the application for deployment, run the following command:
```bash
mvn -f application jpro:release
```
9. The application release zip will be built and the output will be in the `application/target` directory, with the name 
`application-jpro.zip`.
10. The application can also run as classic desktop JavaFX app with the following command:
```bash
mvn -f application javafx:run
```

Note: Komet requires sample data to operate with full functionality
