cfp-devoxx-fr
=============

Le CFP de Devoxx France est codé en Scala, avec le framework Play 2.2.x. Les données sont persistées sur Redis 2.6.

J'ai écris cette application en prenant soin de rester simple, pragmatique et productif.

## Caractéristiques et idées du nouveau CFP :

- Authentification et inscription possible via OpenID (Google et Github)
- Utilisation du format Markdown pour saisir sa proposition de sujet
- Les photos des speakers sont tirées du site Gravatar
- Importation du profil Google+ ou Github pour accélérer la création du profil
- Pas de framework JS côté client sauf si cela devient une nécessité
- Du code Scala simple et facile à lire

## Installer un environnement de développement local

L'installation d'un environnement de dév est simple et s'effectue en quelques étapes :

- installer Play 2.2.x
- installer Redis 2.6.16
- configurer son serveur Redis pour être "slave" de la prod
- récupérer le code source du projet CFP Devoxx France de Bitbucket
- lancer et commencer à contribuer

## Installation de Play 2.2

Pré-requis : Java 7 fortement conseillé pour des raisons de performances.

- Téléchargez Play 2.2.1 http://downloads.typesafe.com/play/2.2.0/play-2.2.0.zip
- Décompressez dans un répertoire, ajouter le répertoire à votre PATH
- Placez-vous dans un nouveau répertoire et vérifiez que Play2 est bien installé avec la commande "play"

## Installation de Redis 2.6.16

Pré-requis : les utilitaires make, gcc correctement installés via XCode ou brew.

- Téléchargez http://download.redis.io/releases/redis-2.6.16.tar.gz
- Décompressez, et suivez les inscrutions du fichier README, pour compiler Redis
- Effectuez un "make install" en tant que root afin d'installer Redis

Je déconseille de tester les installations via Brew, qui ne sont pas correctement configurées. Vous allez perdre du temps.

## Configurer votre serveur Redis

Lorsque vous développez sur votre machine, nous allons utiliser un serveur Redis local afin de pouvoir y écrire nos données, sans perturber la production.
Cependant, un des points forts de Redis, c'est qu'il est possible de synchroniser son serveur local avec un serveur Redis distant.
Pour cela, nous allons déclarer que la prod, le serveur Redis hébergé à distance, est MASTER. Votre installation de Redis
locale sera donc SLAVE de ce serveur. Redis synchronise rapidement, et vous aurez donc en permanence une copie de la prod sur votre machine locale.

Pour cela, il faut utiliser le fichier conf/redis-devoxxfr.conf que j'ai placé dans le répertoire conf du projet Play2.
Prenez ce fichier, copiez-le vers le répertoire par défaut de Redis, /usr/local/etc sur MacOS X.

Vous pouvez alors démarrer le serveur redis local avec la commande suivante :

    redis-server /usr/local/etc/redis-devoxxfr.conf

Pour arrêter proprement le serveur, il suffit d'envoyer la commande SHUTDOWN au serveur :

    redis-cli -p 6363 SHUTDOWN


Vous pouvez vous amuser avec votre serveur Redis en local avec l'utilitaire "redis-cli"

Celui-ci permet d'effectuer des commandes, je vous invite à suivre le petit tutorial http://try.redis.io/ pour mieux comprendre.

# Git et gestions des branches

# Contributeurs

    Nicolas Martignole @nmartignole
    Gabriel Kastenbaum @lambdadevfr

---
Fin du document
