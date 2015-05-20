Call for Paper application for Devoxx
=============

Online document
===============
(https://docs.google.com/document/d/1X0q0limVxdIE65pTCjpz3y5wLznn_Ra1PsortYSDTOQ/edit?usp=sharing)

cfp-devoxx-fr
=============

Original author: Nicolas Martignole @nmartignole

English
-------

The CFP was originally created in 2014 for Devoxx France. Implemented with Scala, the CFP uses the Play Framework v2.2.3
and Redis 2.8 to persist datas. The application focus on simplicity and pragmatism.

## How to set-up a local and friendly developer environment ?

- Install Play 2.2.3 (not the latest version with activator)
- Install Redis 2.8.4, do not use "brew install redis" on Mac, as it would install 2.6, an older version of Redis
- Read Redis documentation and learn Redis with http://try.redis.io
- Read also the self-document redis.conf https://raw.githubusercontent.com/antirez/redis/2.8/redis.conf

optional but recommended for better user experience:
- Install ElasticSearch
- Create Github App and configure OAuth
- Create a Google App and configure OAuth

## What do I need?

To work on a local CFP, you don't need to configure the Github,Google or LinkedIn OAuth application. You can
just start a local version of the CFP, and configure later those components.
Elastic-search is not required, but you won't be able to search from the CFP Admin panel

## Where do I start?

First things first, you need to set-up your own conference. To do so, Frederic Camblor implemented a generic
class that contains most (but not all) importants details. Check ConferenceDescriptor.scala. This file defines
the configuration of your own conference.

You can then also translate and check messages/messages.fr from the conf file

## In term of Git, how can I push a new feature?

The main development branch is dev. It's either Devoxx FR or Devoxx BE. It' an out-of-the-box ready to use
conference. This is also where I try to collect all features from all contributors.

I created a branch dev-france for current development regarding Devoxx France. I did the same for BE. Depending
if we're in november or march, I use one or the other as my main active branch.

I work with one feature per branch, then do local merge.

When you want to update your local branch (for instance, dev-poland) you should :

    - do a checkout of dev
    - do a git pull --rebase on dev
    - switch to dev-poland
    - do a git pull --rebase from dev to dev-poland so that you keep your local updates

For Pull request, see https://www.atlassian.com/git/tutorials/making-a-pull-request/

French
------

Le CFP de Devoxx Poland est codé en Scala, avec le framework Play 2.2.x. Les données sont persistées sur Redis 2.8.

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

- installer Play 2.2.3
- installer Redis 2.8.4
- configurer son serveur Redis pour être "slave" de la prod
- récupérer le code source du projet CFP Devoxx Poland de Bitbucket
- lancer et commencer à contribuer

## Installation de Play 2.2

Pré-requis : Java 7 fortement conseillé pour des raisons de performances.

- Téléchargez Play 2.2.3 http://downloads.typesafe.com/play/2.2.3/play-2.2.3.zip
- Décompressez dans un répertoire, ajouter le répertoire à votre PATH
- Placez-vous dans un nouveau répertoire et vérifiez que Play2 est bien installé avec la commande "play"

## Installation de Redis  2.8.4

Pré-requis : les utilitaires make, gcc correctement installés via XCode ou brew.

- Téléchargez http://download.redis.io/releases/redis-2.8.4.tar.gz
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

# Reporter un bug

Le projet est hébergé sur [Bitbucket](https://bitbucket.org/nicolas_martignole/cfp-devoxx-fr)

# Installer sa propre version du CFP

Imaginons que vous êtes Breton et que vous souhaitiez installer ce CFP pour votre conférence :-)

Pour pouvoir faire tourner en production votre propre application du CFP, il est nécessaire de configurer différents éléments dans le fichier **application.conf** de Play 2.2.

Voici ce qu'il vous faut

- un compte [Mailjet](http://www.mailjet.fr) pour l'envoi SMTP des emails transactionnels
- un compte [Github](http://www.github.com) pour pouvoir créer une clé API pour l'authentification OpenID. Voir [https://github.com/settings/applications](cette page)
- créer une application via votre compte Google sur [https://cloud.google.com/console#/project](https://cloud.google.com/console#/project) . Configurez une URL pour le développement, comme http://localhost:9000/ et une URL de prod comme http://cfp.devoxx.pl/
- un serveur Redis protégé par un mot de passe très long
- un serveur ElasticSearch

Voici ce que vous devez configurer :

- Renommez le fichier **application-please-customize-me.conf** en **application.conf**
- Générez une chaîne de caractère pour la sécurité de l'application

    application.secret="a_unique_secret"

- Configurez le serveur SMTP en prenant les paramètres de Mailjet
- Configurez la partie Github
- Configurez la partie Google pour l'authentification OAuth2.0
- Configurez enfin le serveur Redis. Prenez soin de configurer un mot de passe très long pour votre serveur Redis
- Configurez enfin l'adresse d'un serveur ElasticSearch

Au moment de la mise à jour de cette documentation, la partie Trello n'est pas encore codée.

# Contributeurs

- Nicolas Martignole [@nmartignole](http://www.twitter.com/nmartignole)
- Gabriel Kastenbaum [@lambdadevfr](http://www.twitter.com/lambdadevfr)

- Jean Helou [@jeanhelou](http://www.twitter.com/jeanhelou) pour la conférence Scala.IO
- Frédéric Camblor [@fcamblor](http://www.twitter.com/fcamblor) pour la conférence BDX.IO

# Hébergement

Le CFP de Devoxx Poland est hébergé sur la plateforme [http://www.clever-cloud.com](Clever-Cloud)


