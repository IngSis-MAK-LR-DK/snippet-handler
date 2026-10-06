# syntax=docker/dockerfile:1

FROM eclipse-temurin:17-jdk AS build
WORKDIR /workspace
COPY . .
# El plugin de ci-kit se baja de GitHub Packages: las credenciales llegan como build secrets
#   docker build --secret id=gpr_user,env=GITHUB_ACTOR --secret id=gpr_key,env=GITHUB_TOKEN .
RUN --mount=type=secret,id=gpr_user,env=GITHUB_ACTOR \
    --mount=type=secret,id=gpr_key,env=GITHUB_TOKEN \
    --mount=type=cache,target=/root/.gradle \
    ./gradlew :app:bootJar --no-daemon

FROM eclipse-temurin:17-jre
WORKDIR /app
RUN useradd --system --no-create-home snippets
USER snippets
COPY --from=build /workspace/app/build/libs/app-*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
