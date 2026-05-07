# Variables para reutilizar
MVN = mvn
APP_NAME = AeroLuggage

.PHONY: build run clean

build: ## Compila el proyecto y genera el JAR
	$(MVN) clean package -DskipTests

run: ## Ejecuta la aplicación Spring Boot
	$(MVN) spring-boot:run

clean: ## Borra la carpeta target
	$(MVN) clean

seed: ## Ejecuta el seed de la base de datos sin limpiar
	$(MVN) exec:java@seed

seed-clean: ## Ejecuta el seed de la base de datos con limpiar
	$(MVN) exec:java@seed -Dargs="clean"