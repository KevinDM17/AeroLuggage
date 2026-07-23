# Variables para reutilizar
MVN = mvn
APP_NAME = AeroLuggage
MVN_OPTS = -Xmx1g

.PHONY: build run clean

build: ## Compila el proyecto
	$(MVN) clean package -DskipTests

run: ## Ejecuta la aplicación
	MAVEN_OPTS="$(MVN_OPTS)" $(MVN) spring-boot:run

clean: ## Borra la carpeta target
	$(MVN) clean

seed: ## Ejecuta el seed sin limpiar
	MAVEN_OPTS="$(MVN_OPTS)" $(MVN) exec:java@seed

seed-clean: ## Ejecuta el seed con limpieza previa
	MAVEN_OPTS="$(MVN_OPTS)" $(MVN) exec:java@seed -Dclean=true

reload-dep: ## Recargar dependencias
	$(MVN) dependency:resolve
