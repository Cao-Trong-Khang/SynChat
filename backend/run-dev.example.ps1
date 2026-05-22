$env:GOOGLE_APPLICATION_CREDENTIALS="path-to-synchat-firebase-adminsdk.json"
$env:FCM_ENABLED="true"
$env:FCM_DRY_RUN="false"
$env:FIREBASE_PROJECT_ID="your-firebase-project-id"
$env:APP_LOG_LEVEL="DEBUG"

.\mvnw spring-boot:run