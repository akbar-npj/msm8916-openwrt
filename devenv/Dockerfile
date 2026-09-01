FROM ubuntu:22.04

LABEL maintainer="OpenWrt Builder"

# -----------------------------------------------------------------------------
# Install build dependencies
# -----------------------------------------------------------------------------

COPY devenv/dependencies.sh /install_dependencies

RUN chmod +x /install_dependencies && \
    /install_dependencies && \
    rm -f /install_dependencies

# -----------------------------------------------------------------------------
# Create a non-root user matching the host UID/GID
# -----------------------------------------------------------------------------

ARG UID=1000
ARG GID=1000

RUN if ! getent group ${GID} >/dev/null; then \
        groupadd -g ${GID} builder; \
    fi && \
    if ! getent passwd ${UID} >/dev/null; then \
        useradd -m -u ${UID} -g ${GID} -G sudo builder; \
    fi && \
    echo '%sudo ALL=(ALL) NOPASSWD:ALL' >> /etc/sudoers

USER builder

# -----------------------------------------------------------------------------
# Default working directory
# -----------------------------------------------------------------------------

WORKDIR /repo/openwrt

CMD ["/bin/bash"]
