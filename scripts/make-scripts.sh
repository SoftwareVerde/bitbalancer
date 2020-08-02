#!/bin/bash

echo -e "#!/bin/bash\n\nexec java -jar bin/main.jar \"\$@\"\n" > out/run.sh
chmod 770 out/*.sh

