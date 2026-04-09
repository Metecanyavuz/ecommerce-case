FROM grafana/k6:latest

# k6 binary /usr/local/bin/k6 olarak gelir
# Script ve proto dosyalarını kopyala
COPY rest-benchmark.js  /scripts/rest-benchmark.js
COPY grpc-benchmark.js  /scripts/grpc-benchmark.js
COPY proto/             /scripts/proto/
COPY run-railway.sh     /scripts/run-railway.sh

WORKDIR /scripts

# Entrypoint override — k6 image'ının default entrypoint'i k6 binary'sidir,
# biz onun yerine shell script çalıştırıyoruz.
ENTRYPOINT ["sh", "/scripts/run-railway.sh"]
