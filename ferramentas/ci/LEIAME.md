# Integração contínua (GitHub Actions)

`android.yml` roda os testes dos parsers e gera o APK de debug a cada push na `main`.

Ele fica aqui porque enviar arquivos para `.github/workflows/` exige que o token do GitHub
tenha o escopo `workflow`. Para ativar:

```bash
gh auth refresh -s workflow          # autoriza o escopo no navegador
mkdir -p .github/workflows
git mv ferramentas/ci/android.yml .github/workflows/android.yml
git commit -m "Ativa a integração contínua"
git push
```
