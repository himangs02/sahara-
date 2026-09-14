FROM python:3.12-slim

WORKDIR /app

COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

COPY app ./app
COPY alembic ./alembic
COPY alembic.ini .
COPY docker-entrypoint.sh .
RUN chmod +x docker-entrypoint.sh

EXPOSE 8000

# DATABASE_URL, JWT_SECRET, JWT_ALGORITHM, ACCESS_TOKEN_EXPIRE_MINUTES must be
# supplied at run time (e.g. `docker run --env-file .env ...`, or via
# docker-compose.yml) -- none are baked into this image.
ENTRYPOINT ["./docker-entrypoint.sh"]
