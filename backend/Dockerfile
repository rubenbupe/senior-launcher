FROM oven/bun:1.2

WORKDIR /app

COPY package.json bun.lock tsconfig.json ./
RUN bun install --frozen-lockfile

COPY . .

EXPOSE 8080

CMD ["bun", "run", "start"]
