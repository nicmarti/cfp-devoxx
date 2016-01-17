/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2016 Fabien Vauchelles
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of
 * this software and associated documentation files (the "Software"), to deal in
 * the Software without restriction, including without limitation the rights to
 * use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of
 * the Software, and to permit persons to whom the Software is furnished to do so,
 * subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS
 * FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR
 * COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER
 * IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN
 * CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

package library

/**
 * Stop words
 * from Python NLTK
 * @author Fabien Vauchelles @fabienv
 */
object Stopwords {

    var english = Set(
        "all", "just", "being", "over", "both", "through", "yourselves", "its", "before",
        "herself", "had", "should", "only", "under", "ours", "has", "them", "his",
        "very", "they", "not", "during", "now", "him", "nor", "did", "this", "she", "each",
        "further", "where", "few", "because", "doing", "some", "are", "our", "ourselves",
        "out", "what", "for", "while", "does", "above", "between", "who",
        "were", "here", "hers", "about", "against", "own",
        "into", "yourself", "down", "your", "from", "her", "their", "there", "been", "whom",
        "too", "themselves", "was", "until", "more", "himself", "that", "but", "don", "with",
        "than", "those", "myself", "these", "will", "below", "can", "theirs",
        "and", "then", "itself", "have", "any",
        "again", "when", "same", "how", "other", "which", "you", "after", "most",
        "such", "why", "off", "yours", "the", "having", "once",
        "let's", "beyond", "started", "get", "lines", "time", "use", "fun", "more",
        "using", "build", "talk", "also", "new", "work", "concepts", "create", "session,",
        "learn", "many", "help", "look", "make", "live", "base", "need", "it's:", "project",
        "model", "we'll", "start", "want", "show", "used", "one", "talk,", "like", "types",
        "tools", "running", "take", "language", "know", "based", "tour", "common", "tool", "demonstrate",
        "dont", "run", "standard"
    )

    var french = Set(
        "fut", "aient", "auraient", "ete", "aurions", "serait", "les", "serais", "mais",
        "eue", "ayante", "eux", "aux", "eus", "etees", "etes", "aurais", "etait",
        "aviez", "ayantes", "etais", "moi", "sont", "mon", "ayant", "serez", "nos", "aurez",
        "eussiez", "furent", "avons", "soient", "leur", "futes", "ayez", "seriez",
        "ses", "sommes", "tes", "aurait", "est", "etaient", "serions", "sur",
        "lui", "meme", "soyons", "ayants", "ayons", "soyez", "que", "mes", "qui",
        "une", "auras", "eut", "son", "auriez", "des", "ont", "avez", "avait", "avec",
        "fussions", "etions", "seraient", "suis", "eussions", "toi", "ton", "eues", "vous", "aies",
        "fusses", "etes", "eumes", "auront", "aurons", "avions", "eutes", "eut", "etee", "fut",
        "fus", "fussent", "ait", "dans", "pour", "seras", "serai", "sera", "aie",
        "avaient", "aurai", "votre", "eusse", "eussent", "eusses", "soit", "etantes",
        "sois", "vos", "par", "pas", "etant", "fussiez", "seront", "fumes", "serons", "aura",
        "avais", "notre", "elle", "nous", "eurent", "fusse", "etants", "etiez", "ces", "etante",
        "comment", "faire", "tout", "pourquoi", "grâce", "introduction", "enfin", "quand",
        "secrets", "d'expérience", "moins", "créer", "savoir", "boîte", "d'un", "universelle",
        "pratique", "simple", "action", "via", "au-delà", "nouveau", "sans", "cette",
        "plus", "découvrir", "bien", "aussi", "presentation", "propose", "comme", "peut",
        "session", "d'une", "encore", "quelques", "outils", "c'est", "verrons", "développement",
        "projet", "être", "tous", "...", "même", "possible", "serveur", "temps", "place",
        "montrer", "manière", "depuis", "utiliser", "permettant", "présentation",
        "permet", "venez", "nouvelle", "mettre", "fait", "ensemble", "monde", "langage", "ainsi",
        "infrastructure", "source", "solutions", "exemples", "chaque", "dès", "mieux",
        "cet", "présentation,", "technologies", "souvent", "peu", "allons", "entre", "très",
        "cela", "pratiques", "plusieurs", "utilisant", "voir", "exemple", "durant",
        "maintenant", "bonne", "d’une", "gestion", "bonnes", "différents", "été", "d’un", "travers",
        "cas", "fonctionnalités", "it's", "different", "comprendre", "mémoire", "quels", "modèle",
        "rapidement", "différentes", "production", "support", "déjà", "afin", "mise", "c’est",
        "j'ai", "devenu", "peuvent", "passer", "outil", "see", "type", "qu'il", "clés",
        "cours", "choix", "donc", "besoins", "montrerai", "machine", "après", "faut"
    )

    var all = (english ++ french)

    var translate = Map(
        "données" -> "data",
        "developers" -> "developer",
        "développeurs" -> "developer",
        "développement" -> "developer",
        "développer" -> "developer",
        "development" -> "developer",
        "développeur" -> "developer",
        "java." -> "java",
        "java," -> "java",
        "applications" -> "software",
        "applications." -> "software",
        "containers" -> "container",
        "plateforme" -> "plateform",
        "sécurité" -> "security"
    )


    def clean(l: List[String]): List[String] = {
        val cleaned = l
            .filter(word => word.length > 2)
            .map(_.toLowerCase)
            .map(word => translate.getOrElse(word, word))

        cleaned.filterNot(all)
    }
}