FROM confluentinc/cp-kafka:7.7.1
COPY --chmod=755 infrastructure/render/kafka/start.sh /usr/local/bin/payflow-kafka
EXPOSE 9092
CMD ["/usr/local/bin/payflow-kafka"]
