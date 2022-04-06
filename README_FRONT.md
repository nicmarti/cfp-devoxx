cfp-devoxxfr
============

Tailwind is used to generate the stylesheets for the website.

# Pre-requisites

Install `nvm`

## Install node v16.4.0 

`nvm install v16.4.0`

After the installation, restart a shell and check that the node version is v16.4.0 : 

```cfp-devoxx git:(dev) ✗ node -v
v16.4.0
```

## Check your version of node

# How to install Tailwind

```npm install```

# For local developpement

If you need to work on the public web pages of the CFP (program, speakers's bio, agenda, etc.), you can use the following command to generate the stylesheets:

```npx tailwindcss -i ./public/css/dvx_tailwind.css -o ./public/css/devoxx_generated_tailwind.css --watch```


## For production before deployment

Make sure to update and to save on Git the generated CSS file.
No extra npm tasks are executed on the production environment at deployment time.

```npx tailwindcss -i ./public/css/dvx_tailwind.css -o ./public/css/devoxx_generated_tailwind.css --minify```

# Trouble shooting

If `npx` returns the following error, you need to install the latest version of node

```cfp-devoxx git:(dev) ✗ npx
<function>:32
  #unloaded = false
  ^
```

If the issue is not fixed, it could be related to an issue betwen jEnv and a local install of GraalVM
See https://github.com/jenv/jenv/issues/294

I had to delete this from .zshrc:

```# Temporary solution for https://github.com/jenv/jenv/issues/294
rm "$HOME/.jenv/shims/npm" "$HOME/.jenv/shims/node"
```

and I also added this to my .zshrc:
```eval $(jenv init --no-rehash -)```

