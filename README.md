# DocTor

It's supposed to sync your local markdown files to notion automatically.

## Local Development

start a node server by running `node target/kleene_ai-doctor.js`

jack-in with your preferred editor, for emacs it's:

`cljs-jack-in` then select `shadow` then `app`

## Build and release

```shell
npm i --include=dev
npm run release
```
