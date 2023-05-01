

### Setting up mongodb for dev

```
./start-dev-db.sh
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
