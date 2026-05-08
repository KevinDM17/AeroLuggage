# Variables para reutilizar
MVN = mvn
APP_NAME = AeroLuggage

.PHONY: build run clean

build: ## Compila el proyecto
	$(MVN) clean package -DskipTests

run: ## Ejecuta la aplicación
	$(MVN) spring-boot:run

clean: ## Borra la carpeta target
	$(MVN) clean

seed: ## Ejecuta el seed sin limpiar
	$(MVN) exec:java@seed

seed-clean: ## Ejecuta el seed con limpieza previa
	$(MVN) exec:java@seed -Dargs="clean"

reload-dep: ## Recargar dependencias
	$(MVN) dependency:resolve