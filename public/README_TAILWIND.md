# Tailwind CSS

The public web pages of the CFP relies on Tailwind.css. You don't have
to customize tailwind and you can directly use it if needed. 

However, if you want to regenerate the css/tailwind.css file : 

Pre-requisite : install npm and node

    npm install -D tailwindcss@latest postcss@latest autoprefixer@latest

## How do I compile the dvx_tailwind.css file to generate tailwind.css ?

Use the following commande

    npx tailwindcss-cli@latest build ./css/dvx_tailwind.css -o ./css/tailwind.css 

More help available [on the tailwind web site](https://tailwindcss.com/docs/installation#using-a-custom-css-file)

