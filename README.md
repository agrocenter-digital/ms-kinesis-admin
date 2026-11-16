# Administrador de Kinesis — preparación local

Microservicio Spring Boot sencillo para consultar streams `agrocenter-`, crearlos y solicitar cambios de retención o shards y eliminación. Devuelve 202 cuando AWS acepta la solicitud: no garantiza que haya terminado. La aplicación no administra consumidores, checkpoints ni métricas de lag.

## Seguridad y límites

Requiere JWT access token válido de Cognito y grupo ADMIN en todas las rutas administrativas. Configure `COGNITO_ISSUER_URI`, `COGNITO_JWK_SET_URI` y `COGNITO_AUDIENCE` privadamente. `AWS_REGION` define la región. El SDK utiliza la cadena de credenciales AWS por defecto; en EC2 se espera perfil de instancia, sin claves en archivos de entorno.

Las escrituras están **deshabilitadas por defecto** (`KINESIS_ADMIN_WRITES_ENABLED=false`); habilitarlas requiere autorización y permisos IAM. Los streams `agrocenter-kinesis-negocio` y `agrocenter-kinesis-stock` están protegidos contra eliminación incluso cuando se habilitan escrituras. No se debe publicar el puerto del servicio en Internet; el BFF debe conectarse a él por la red interna de Compose y ambos validan el token. Los controles no sustituyen IAM de mínimo privilegio.

Para lectura se necesitan permisos Kinesis `ListStreams` y `DescribeStreamSummary`. Para operaciones habilitadas se requieren además `CreateStream`, `UpdateShardCount`, `IncreaseStreamRetentionPeriod`, `DecreaseStreamRetentionPeriod` y `DeleteStream`, sujetos a los recursos y políticas permitidos por AWS. El rol de instancia se comparte con los demás contenedores: evaluar aislamiento y alcance antes de ampliar permisos. No se ha comprobado LabRole ni autorizado cambios IAM.

## Integración pendiente

El BFF usa `services.kinesis-admin.base-url` (por defecto `http://localhost:8087`); en Compose debe apuntar al nombre de servicio interno. Definir una imagen publicada por digest y un Compose candidato, con las variables Cognito privadas y sin puerto público. Todavía no existe configuración de despliegue autorizada ni prueba con AWS real. No recrear BFF ni servicios existentes sin coordinación.

Ejecutar `mvn test` para pruebas locales con AWS simulado. Antes de activar escrituras se requieren pruebas integradas de autenticación, errores y operaciones sobre recursos de prueba explícitamente autorizados, revisión de costos y una estrategia de seguimiento para respuestas ambiguas. Nunca repetir una operación sin consultar antes el estado real.
