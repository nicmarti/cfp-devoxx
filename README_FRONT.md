cfp-devoxxfr
============

Tailwind is used to generate the stylesheets for the website and the home page.

# Pre-requisites

- Install `nvm`
- Install and activate the correct version of nodejs
- Install `npm` 
- Install `tailwindcss` and its dependencies
- Install `postcss` and its dependencies
- Install `autoprefixer` and its dependencies
  
## Install node v16.4.0 

```
nvm install v16.4.0
v16.4.0 is already installed.
Now using node v16.4.0 (npm v6.13.4)
```

Load the version from the `.nvmrc` file

```
nvm use
Found '~/Dev/cfp-devoxx/.nvmrc' with version <16.4.0>
Now using node v16.4.0 (npm v6.13.4)
```

After the installation, restart a shell and check that the node version is v16.4.0 : 

```
npm -v 
6.13.4

```

## How to install Tailwind

Update browserlits :

```
npx browserslist@latest --update-db
```

Then :

```
npm install --save-dev "browserslist@>= 4.21.0" 
```

Install Tailwind and additional modules :

```
npm install -D tailwindcss postcss autoprefixer
```

This is the version we used : 
+ tailwindcss@3.2.4
+ autoprefixer@10.4.13
+ postcss@8.4.19

# For local developpement

## For the main CFP website

```
npx tailwindcss -i ./tailwind/src/cfp_devoxx_fr_2023.css -o ./public/devoxx_fr_2023/devoxx_fr_2023.css --watch
```

## Public web pages for program

If you need to work on the public web pages of the CFP (program, speakers's bio, agenda, etc.), you can use the following command to generate the stylesheets:

```
npx tailwindcss -i ./public/css/dvx_tailwind.css -o ./public/css/devoxx_generated_tailwind.css --watch
```

## CFP pages


## For production before deployment

Make sure to update and to save on Git the generated CSS file.
No extra npm tasks are executed on the production environment at deployment time.

```

npx tailwindcss -i ./tailwind/src/dvx_tailwind.css -o ./public/css/devoxx_generated_tailwind.css --minify

npx tailwindcss -i ./tailwind/src/cfp_devoxx_fr_2023.css -o ./public/devoxx_fr_2023/devoxx_fr_2023.css --minify
```

# Trouble shooting on MacOS 

If `npx` returns the following error, you need to install the latest version of node

```
cfp-devoxx git:(dev) ✗ npx
<function>:32
  #unloaded = false
  ^
```

If the issue is not fixed, it could be related to an issue betwen jEnv and a local install of GraalVM
See https://github.com/jenv/jenv/issues/294

If you get this error 
```
➜  cfp-devoxx git:(dev) ✗ npx tailwindcss -i ./tailwind/src/cfp_devoxx_fr_2023.css -o ./public/devoxx_fr_2023/devoxx_fr_2023.css --watch
<function>:67414
          const { default: imported } = await import(pathToFileURL(configFile));
                                           
```
Then do the following : 

```# Temporary solution for https://github.com/jenv/jenv/issues/294
rm "$HOME/.jenv/shims/npm" "$HOME/.jenv/shims/node"
```

and I also added this to my .zshrc:
```eval $(jenv init --no-rehash -)```

