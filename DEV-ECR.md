# DEV ECR 직접 게시

서비스 코드 빌드·검증 후 동일 로컬 이미지를 Actions artifact로 전달해 ECR에 직접 게시합니다.
- 저장소: `443845329525.dkr.ecr.ap-northeast-2.amazonaws.com/nineouting-dev-api-gateway`
- 기존 OIDC 역할과 게시 조건을 유지합니다.
- DEV 게시 이미지는 `linux/amd64`입니다. Python의 기존 AMD64·ARM64 모델/코드 검증은 유지합니다.
- 태그는 `sha-<commit>-<run>-<attempt>`이며 manifest의 config digest와 로컬 이미지 ID, 다시 받은 이미지 ID·플랫폼을 검증합니다.
- `dev-ecr-image-<run>-<attempt>` artifact와 `image-result.txt`의 기존 4개 항목을 유지합니다.
- Gateway는 기존처럼 ECR 게시까지만 수행합니다. 새로운 GitOps 배포 경로를 추가하지 않습니다.
- 공통 모듈 Maven 저장소와 `GPR_TOKEN`은 유지합니다.

PR은 검증만 하며 ECR에 게시하지 않습니다. 이 변경은 Argo CD 정책이나 `prune=false`를 수정하지 않습니다.
Python 릴리스 태그는 이미지 검증만 수행하며 DEV ECR 게시 대상이 아닙니다.

검사: `actionlint`, `shellcheck .github/scripts/publish-dev-ecr.sh`, `python3 .github/scripts/test_publish_dev_ecr.py`.
