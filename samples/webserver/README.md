# WebServer Operator

This is a more complex example of how a Custom Resource backed by an Operator can serve as
an abstraction layer. This Operator will use an webserver resource, which mainly contains a
static webpage definition and creates a nginx Deployment backed by a ConfigMap which holds
the html.

This is an example input:
```yaml
apiVersion: "sample.javaoperatorsdk/v1"
kind: WebServer
metadata:
  name: mynginx-hello
spec:
  html: |
    <html>
      <head>
        <title>Webserver Operator</title>
      </head>
      <body>
        Hello World!!
      </body>
    </html>
```
