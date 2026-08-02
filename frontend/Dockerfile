FROM node:20-alpine

WORKDIR /app

# Paket dosyalarını kopyala ve kurulumları yap
COPY package.json ./
RUN npm install

# Kalan tüm dosyaları kopyala
COPY . .

# Vite varsayılan portunu dışa aç
EXPOSE 3000

# Geliştirme sunucusunu Docker içinde her yere açık (--host) şekilde başlat
CMD ["npm", "run", "dev", "--", "--host"]
