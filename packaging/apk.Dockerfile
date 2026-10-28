# OCI archive containing only the signed Android APK. Not a runnable container.
FROM scratch
LABEL org.opencontainers.image.source="https://github.com/xavier-le7/screenguard-android-agent"
LABEL org.opencontainers.image.description="ScreenGuard Android Agent signed APK distribution archive"
COPY app.apk /screenguard-android-agent.apk
