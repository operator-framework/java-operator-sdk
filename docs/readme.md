## Run website locally
To run website locally run first `bundle install`and then `bundle exec jekyll serve`

## CSS library

The website uses [UIkit](https://getuikit.com/) as the css library.
inside _sass folder you will find:
* `/theme` folder which contains personalised variables, mixins and components used 
(unused components, that can be used in the future, are commented out for size benefits)
* `/uikit` folder which contains the library default SCSS files.
 
## Navigation

The navigation bars (main menu, footer) make use of the `/_data/navbar.yml` file, here you can write down the title and url where you want to page to be reached at. It will automatically be added to the website.
Please make sure the `url` here is the same as the `permalink` inside the page front matter.

For example:

`navbar.yml` -
```title: Docs
url: /docs/getting-started
```
`docs/getting-started.md` -
```---
title: java-operator-sdk
description: Build Kubernetes Operators in Java without hassle
layout: docs
permalink: /docs/getting-started
---
```

In order to create a navigation dropdown follow the following example:
```- title: Docs
    url: /docs/getting-started
    dropdown:
     - title: Getting started
         url: /docs/docs
      - title: Examples
         url: /docs/examples/
```
The sidebar for the docs pages makes use of `/_data/sidebar.yml` in the same manner as explained previously for the navigation bars.

## Page Layouts

There are three page layouts:
* `homepage` this is very specific to the homepage and shouldn't be used for other pages
* `docs` this is specific to all the pages that are related to documentation files.
* `default` this can be reused for any other pages. Mention the title in the Front Matter and omit it in the content of your page. 

## Documetation pages

All documentation files should be added to the `documentation/` folder and for the navigation to have `/docs/page-name` in the url.


## Github api

The website uses the [jekyll-github-metadata](https://github.com/jekyll/github-metadata) plugin in order to display new releases automatically, 
this can be use for other purposes if need arises