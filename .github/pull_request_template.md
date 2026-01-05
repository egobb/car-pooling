# Pull Request

## Description
<!-- Describe what this PR does and why -->

## Type of Change
<!-- Check all that apply -->
- [ ] ✨ feat (new feature)
- [ ] 🐛 fix (bug fix)
- [ ] 🛠️ chore (build, ci, tooling, config)
- [ ] 📖 docs (documentation only)
- [ ] 🔄 refactor (code change that neither fixes a bug nor adds a feature)
- [ ] ✅ test (adding or fixing tests)

## Checklist
- [ ] Title follows **Conventional Commits** (`feat: ...`, `fix: ...`, `chore(ci): ...`, etc.)
- [ ] PR targets the correct branch (`main` for release, `develop` for ongoing work)
- [ ] Code builds and tests pass (`make test`)
- [ ] Code is formatted (`make fmt`)

---

⚠️ **Reminder:**  
PRs merged into `main` will trigger **semantic-release**.
- `feat:` → bump **minor** version
- `fix:` → bump **patch** version
- `chore:/docs:/refactor:/test:` → no version bump (unless combined with feat/fix)
