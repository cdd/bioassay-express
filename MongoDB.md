# Add authentication to mongo DB

First, setup an admin user for mongo DB.
```
mongo
use admin
db.createUser(
  {
    user: "myUserAdmin",
    pwd: "abc123",
    roles: [ { role: "userAdminAnyDatabase", db: "admin" } ]
  }
)
```
Connect to DB
```
mongo --port 27017 -u "myUserAdmin" -p "abc123" --authenticationDatabase "admin"
```
and add BAE user
```
use bae
db.createUser(
  {
    user: "baeUser",
    pwd: "12345678",
    roles: [
     { role: "readWrite", db: "bae" }
    ]
  }
)
```
`user` and `password` can either be added to the database section of the `config.json` file or provided with environment variables.