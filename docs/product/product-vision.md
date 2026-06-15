# Quoti

**Réagis partout, sans perdre le contexte.**

---

# Vision

Les conversations sont aujourd'hui fragmentées entre X, Threads, Bluesky, LinkedIn, Mastodon et d'autres plateformes.

Lorsqu'un utilisateur souhaite réagir à un contenu publié sur une plateforme différente de celle où il s'exprime, le contexte est souvent perdu.

Les abonnés voient une réaction mais ne savent pas à quoi elle répond.

Quoti résout ce problème.

L'extension transforme n'importe quel post en une carte élégante, partageable et compréhensible partout.

Au lieu d'une simple capture d'écran brute, Quoti génère une représentation visuelle premium du contenu source, permettant à l'utilisateur de poursuivre une conversation sur n'importe quelle plateforme tout en conservant le contexte original.

---

# Description du produit

Quoti est une extension navigateur qui permet de :

* capturer un post depuis une plateforme sociale
* générer automatiquement une carte visuelle élégante
* ajouter sa propre réaction ou commentaire
* partager le résultat sur une autre plateforme

L'objectif n'est pas de faire du cross-posting.

L'objectif est de rendre les conversations transportables.

---

# Proposition de valeur

Aujourd'hui :

> "Je réponds à un tweet sur Threads."

Les utilisateurs Threads ne comprennent pas forcément de quoi je parle.

Avec Quoti :

> "Je réponds à un tweet sur Threads en montrant le contexte."

Tout le monde comprend immédiatement la conversation.

---

# Cas d'usage

## X → Threads

Je vois un tweet intéressant.

Je clique sur :

> Réagir ailleurs

Quoti génère une carte du tweet.

J'ajoute mon commentaire.

Je publie sur Threads.

---

## Threads → X

Je vois une discussion sur Threads.

Je souhaite la commenter sur X.

Quoti crée une carte contextuelle.

Je publie ma réaction.

---

## Bluesky → LinkedIn

Je vois une réflexion pertinente.

Je souhaite en discuter avec mon réseau professionnel.

Quoti transporte le contexte.

---

# Philosophie produit

Quoti n'est pas :

* un outil de growth hacking
* un outil d'automatisation
* un outil de spam social
* un gestionnaire de réseaux sociaux

Quoti est :

* un outil de conversation
* un outil de contexte
* un outil de citation moderne
* un outil de publication assistée

---

# Direction artistique

## Editorial Craft

Une identité premium, chaleureuse et moderne.

Inspirations :

* magazine haut de gamme
* carnet de notes
* presse éditoriale moderne
* design numérique contemporain

---

## Valeurs visuelles

### Premium

Sans être luxueux.

### Moderne

Sans être futuriste.

### Original

Sans être extravagant.

### Vivant

Sans être enfantin.

---

## Ambiance

85% sérieux

15% personnalité

---

## Éléments graphiques

* coins généreusement arrondis
* formes légèrement organiques
* animations douces
* ombres sculptées discrètes
* espaces généreux
* hiérarchie typographique forte
* interactions fluides

---

## Ce qu'on évite

* néobrutalisme agressif
* surcharge d'effets IA
* cyberpunk
* interfaces ultra-techniques
* dark mode obligatoire
* surcharge de verre dépoli

---

# Expérience utilisateur

## Mode rapide

Workflow :

1. Survol d'un post
2. Clic sur Quoti
3. Carte générée
4. Copie automatique

Temps cible :

moins de 3 secondes.

---

## Mode composition

Workflow :

1. Sélection du post
2. Édition
3. Ajout d'un commentaire
4. Choix du format
5. Export

Formats :

* carré
* portrait
* paysage

---

## Mode collection (v2)

Sauvegarde de plusieurs posts pour :

* préparer un thread
* créer un carrousel
* préparer une publication plus longue

---

# Fonctionnalités MVP

## Extraction du post

Récupération :

* auteur
* pseudo
* contenu
* date
* lien
* plateforme

---

## Génération de cartes

Création d'une représentation :

* propre
* lisible
* cohérente

Sans utiliser un screenshot brut.

---

## Export

* Copier image
* Télécharger PNG
* Copier texte
* Copier lien

---

## Ouverture rapide

Boutons :

* Ouvrir Threads
* Ouvrir X
* Ouvrir Bluesky
* Ouvrir LinkedIn

---

# Architecture technique

## Principe fondamental

Tout fonctionne dans le navigateur.

Pas de serveur.

Pas de backend.

Pas de base de données.

Pas d'API sociale.

---

# Stack recommandée

## Extension

* TypeScript
* Vite
* Manifest V3

---

## Interface

* React
* CSS Modules
* Motion

Pas besoin de Tailwind.

---

## Génération graphique

* HTML → Canvas
* SVG
* PNG

Bibliothèques possibles :

* html-to-image
* dom-to-image

---

# Architecture

```text
Navigateur
│
├── Content Script
│     └── Lecture du post
│
├── Popup React
│     └── Interface Quoti
│
├── Card Generator
│     └── Création de la carte
│
└── Clipboard Manager
      └── Copie image / texte
```

---

# Développement MVP

## Phase 1

Support :

* X uniquement

Fonctionnalités :

* détection du post
* génération de carte
* export PNG

Objectif :

valider l'intérêt.

---

## Phase 2

Support :

* X
* Threads
* Bluesky

Ajouts :

* thèmes
* commentaires
* formats multiples

---

## Phase 3

Application mobile compagnon.

Objectif :

* Android en premier
* iOS ensuite
* interface adaptee a chaque OS
* Quoti disponible dans l'action systeme Partager
* ouverture de Quoti avec le contexte partage
* generation, edition, export et partage de la carte depuis mobile

La roadmap detaillee vit dans `docs/roadmap/mobile-app-phase-3.md`.

---

## Phase ulterieure

Ajouts possibles :

* historique local
* favoris
* bibliotheque de cartes

Toujours sans backend.

---

# Monétisation potentielle

Le MVP peut rester entièrement gratuit.

Plus tard :

### Gratuit

* export standard
* formats classiques

### Pro

* thèmes premium
* branding personnalisé
* bibliothèque cloud
* génération de carrousels
* templates avancés

---

# Résumé

**Quoti** est une extension navigateur qui permet de transporter le contexte d'une conversation entre réseaux sociaux.

Au lieu de publier une réaction isolée, l'utilisateur partage une carte élégante du contenu auquel il répond.

Le produit est pensé pour être :

* rapide
* beau
* léger
* utilisable quotidiennement
* quasiment gratuit à opérer

Une phrase résume parfaitement le projet :

> **Les conversations méritent de voyager avec leur contexte.**
