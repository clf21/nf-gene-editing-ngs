#!/bin/sh

if ! [ -e "$1/setup.py" ] || ! [ "$2" ]; then
  >&2 echo "Usage: install-CRISPResso.sh SOURCE_DIR VIRTUALENV_DIR"
  exit 1
fi

# Create the virtualenv and activate it
virtualenv "$2"
. "$2/bin/activate"

# To avoid dependency problems, we have to manually install NumPy first,
# even though it should be correctly handled by pip. The version is
# pinned to v1.14.6, which pip resolved as the best match against the
# CRISPResso dependencies.
pip install numpy==1.14.6

# To get SciPy to install, with Alpine's GCC 10, we need to pass the
# -fallow-argument-mismatch flag to gfortran. We create a wrapper to do
# this and place it at the highest priority in our PATH.
GFORTRAN="$(which gfortran)"
GFORTRAN_PATCH="$(mktemp -d)"
cat >"${GFORTRAN_PATCH}/gfortran" <<EOF
#!/bin/sh
exec "${GFORTRAN}" -fallow-argument-mismatch "\$@"
EOF

chmod +x "${GFORTRAN_PATCH}/gfortran"
PATH="${GFORTRAN_PATCH}:${PATH}"

# CRISPResso should now install cleanly
cd "$1"
python setup.py install
