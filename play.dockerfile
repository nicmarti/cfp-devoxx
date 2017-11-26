FROM openjdk:8

ENV PLAY_VERSION 2.2.6

RUN wget -q https://downloads.typesafe.com/play/${PLAY_VERSION}/play-${PLAY_VERSION}.zip \
    && unzip -q play-${PLAY_VERSION}.zip \
    && rm play-${PLAY_VERSION}.zip \
    && ln -s /play-${PLAY_VERSION}/play /usr/local/bin/
