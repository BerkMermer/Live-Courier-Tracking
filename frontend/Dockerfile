FROM node:20-alpine

WORKDIR /app

# Lockfile ile tekrarlanabilir bağımlılık kurulumu
COPY package.json package-lock.json ./
RUN npm ci

# Kalan tüm dosyaları kopyala
COPY . .

# Vite varsayılan portunu dışa aç
EXPOSE 3000

# Geliştirme sunucusunu Docker içinde her yere açık (--host) şekilde başlat
CMD ["npm", "run", "dev", "--", "--host"]
