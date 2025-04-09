## Codeowners

This directory contains an `OWNERS` file based on [Space CODEOWNERS syntax](https://www.jetbrains.com/help/space/code-owners.html#codeowners-file-syntax) with additional requirements:
1. Team names have to be in quotations
2. Individual owners have to be added via email address which is linked to a GitHub account (it can be a secondary email for the account)

This file supports a `virtual team` setup, where 3rd party contributors outside JetBrains organization can be added
to `virtual-team-mapping.json` for custom team management.

Upon making changes to `OWNERS` or `virtual-team-mapping.json` files, run `convert-owners.sh` script to regenerate respective GitHub and Space files.