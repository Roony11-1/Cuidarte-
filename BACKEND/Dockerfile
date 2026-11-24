FROM node:18-alpine

WORKDIR /app

# Copiar archivos de dependencias
COPY package*.json ./

# Instalar dependencias
RUN npm ci --only=production

# Copiar código de la aplicación
COPY src/ ./src/
COPY sql/ ./sql/

# Crear directorios de carga dentro de la imagen. El contenido real se monta
# como volumen desde ./BACKEND/uploads (ver docker-compose.yml); no se copia al
# construir la imagen para no empaquetar documentos de pacientes.
RUN mkdir -p uploads/documentos uploads/contracts

# Exponer puerto
EXPOSE 3000

# Comando para iniciar la aplicación
CMD ["node", "src/app.js"]
