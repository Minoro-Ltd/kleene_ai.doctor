# Jumper

Jumper it's a web service which handles all Kleene connectors lifecycle
(e.g. CRUD operations, publishing to the production, versioning etc.)
Jumper service is responsible for:

- creating the ECS task definitions for the connectors
- adding ingest record to the database
- publishing connector to the production

Also, Jumper has a `job` part which is the base for all connectors.
The code under the `/job` folder will be packaged in the uberjar and stored inside a Docker image (AWS ECR). This Docker
image will be used for running connectors jobs (currently only API connectors) on the AWS Fargate service

### Development

Setup these environment variables:
`PORT` `AWS_ACCESS_KEY_ID` `AWS_SECRET_ACCESS_KEY` `AWS_REGION` `DEV_APP_HOST` `PROD_APP_HOST`
`KLEENE_LOG_ENDPOINT` `PAPERTAIL_TOKEN` `RUNTIME_ENVIRONMENT`

or use a local config file `dev/resources/local.edn`

```clojure
{:ai.kleene.web/config
 {:port                  3000
  :aws-access-key-id     "xxx"
  :aws-secret-access-key "xxx"
  :aws-region            "xxx"
  :dev-app-host          "xxx"}

 :ai.kleene.web/lumber
 {:kleene-log-endpoint "xxx"
  :papertail-token     "xxx"
  :environment         "local"
  :service-name        "Jumper"}}

```

- clone the repo locally
- start the REPL
- go to the dev namespace `(dev)`
- start the dev server `(go)`
- server will be available on the `http://localhost:3000`
- restart the server after changes `(reset)`

The routes' configuration specified in the config file (`resources/config.edn`) under the `:ai.kleene.web/pedestal-routes` key.
The keyword used inside route map e.g. `:jumper.handlers/health-check` it's a name of the corresponding handler.
Handlers are defined with use of the `ai.kleene.web.core/defhandler` macro.

### Release
Release process for Jumper consists of two parts:
- web service
- connector job image

Both done via the CI pipeline.
Docker configuration file for the web service - `docker/server/Dockerfile`
Docker configuration file for the job - `docker/job/Dockerfile`

Service build command `lein uberjar`
Job build command `lein build-job`
