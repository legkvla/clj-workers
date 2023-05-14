# Introduction to clj-workers

Boilerplate to organize fault tolerant concurrent data processing on Clojure.

## Config

### Dev

```
cp profiles-templates.clj profiles.clj
```

* Setup admin token and mongodb (see below)

### Prod

Project uses environ - so you can use env vars instead of config file in prod.
For production - please regenerate private/public keys and make good admin token.

## MongoDb

At the moment only mongodb used as persistence storage

### Setting up mongodb database for dev

You can use any instance of mongodb you have or run instance using

```
docker-compose up -d
```

To setup db:

```
use admin
db.auth('root','secret')
use demo
db.createUser(
   {
     user: "demo",
     pwd: passwordPrompt(),
     roles: [ "readWrite", "dbAdmin" ]
   }
)
```

## Running up

Once you setup db you can run using ``lein run``

Open http://localhost:3002 - Swagger will be opened by default.

You can authenticate using admin token.
After that - you can submit sensor (sample worker) and look in stdout how it is processing (see samples package for details).

## Alerts

There are two way to configure errors reporting:

* mongo transport (set in configuration by default)
* errbit transport (see profiles-templates.clj and clj-workers.alerts for details)
