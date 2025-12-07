


# Others

## Wrong tag for PG server docker image

The following command didn't work in ASSIGNMENT pdf.
```bash
docker run --rm -p 8080:8080 -it public.ecr.aws/b5s9k2b6/nut-2025-1:serverlatest
```

The tag should be replaced with `server-latest` instead of `serverlatest`.

```
public.ecr.aws/b5s9k2b6/nut-2025-1:server-latest
```
* source from: https://gallery.ecr.aws/b5s9k2b6/nut-2025-1
