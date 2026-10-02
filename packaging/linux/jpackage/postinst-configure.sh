        # ColorPicker: "@COMMAND@" command in the PATH
        if [ ! -e @COMMAND_LINK@ ] || [ -L @COMMAND_LINK@ ]; then
            ln -sf @LAUNCHER@ @COMMAND_LINK@
        fi
