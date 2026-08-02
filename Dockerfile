# Build Stage
FROM eclipse-temurin:17-jdk-alpine AS build
WORKDIR /app

# Maven wrapper ve konfigürasyon dosyalarını kopyala
COPY mvnw .
COPY .mvn .mvn
COPY pom.xml .

# mvnw dosyasına çalıştırma izni ver
RUN chmod +x mvnw

# Sadece pom.xml'i kullanarak bağımlılıkları indir (Docker layer cache'inden faydalanmak için)
RUN ./mvnw dependency:go-offline -B

# Proje kaynak kodlarını kopyala ve derle (testleri atlayarak)
COPY src src

# -q BİLİNÇLİ OLARAK KULLANILMIYOR: -q (quiet) modu, derleme sırasında oluşan
# "cannot find symbol", "duplicate class", "file does not contain class" gibi
# hataların STDOUT'ta görünmesini bastırır ve `docker build` çıktısında sadece
# genel bir "BUILD FAILURE" görürsünüz — kök nedeni göremezsiniz. Tam log
# almak, tam olarak bu tip zincirleme derleme hatalarını teşhis etmenin
# ilk adımıdır.
RUN ./mvnw -DskipTests package

# Run Stage
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app

# Güvenlik (Security): Root kullanıcısı yerine yetkisiz bir 'spring' kullanıcısı ve grubu oluştur
RUN addgroup -S spring && adduser -S spring -G spring
USER spring:spring

# Build aşamasında 'target' klasöründe üretilen jar dosyasını 'app.jar' olarak buraya al
COPY --from=build /app/target/*.jar app.jar

# Uygulamanın 8080 portunda çalışacağını belirt
EXPOSE 8080

# Container ayağa kalktığında çalıştırılacak komut
ENTRYPOINT ["java", "-jar", "app.jar"]