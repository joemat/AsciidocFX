/* Generated by Opal 0.6.3 */
(function ($opal) {
    var $a, self = $opal.top, $scope = $opal, nil = $opal.nil, $breaker = $opal.breaker, $slice = $opal.slice, $klass = $opal.klass, $hash2 = $opal.hash2;

    if ($scope.RUBY_ENGINE['$==']("opal")) {
    }
    ;
    self.$include((($a = $opal.Object._scope.Asciidoctor) == null ? $opal.cm('Asciidoctor') : $a));
    (function ($base, $super) {
        function $ChartBlockMacro() {
        };
        var self = $ChartBlockMacro = $klass($base, $super, 'ChartBlockMacro', $ChartBlockMacro);

        var def = self._proto, $scope = self._scope;

        self.$use_dsl();

        self.$named("chart");

        return (def.$process = function (parent, target, attrs) {

                var $a, self = this, target = nil, type = nil, title = nil, filename = nil, alt = nil, caption = nil, width = nil, height = nil, opt = nil, scale = nil, align = nil, cache = nil, csvFile = nil, chartType = nil, imagesdir = nil;

                csvFile = "" + (attrs['$[]']("data-uri"));
                chartType = "" + target;
                title = "" + (attrs['$[]']("title"));
                alt = "" + (attrs['$[]']("alt"));
                caption = "" + (attrs['$[]']("caption"));
                width = "" + (attrs['$[]']("width"));
                height = "" + (attrs['$[]']("height"));
                scale = "" + (attrs['$[]']("scale"));
                align = "" + (attrs['$[]']("align"));
                type = "" + (attrs['$[]']("type"));
                filename = "" + (attrs['$[]']("file"));
                cache = "" + (attrs['$[]']("cache"));
                opt = "" + (attrs['$[]']("opt"));
                imagesdir = parent.$document().$attr('imagesdir', '');

                target = parent.$image_uri(filename);

                if (cache != "enabled") {
                    afx.chartBuildFromCsv(csvFile, imagesdir, target, chartType, opt);
                }

                var attributesHash = {
                    "target": filename,
                    "title": title,
                    "alt": alt,
                    "caption": caption,
                    "width": width,
                    "height": height,
                    "scale": scale,
                    "align": align,
                    "opt": opt
                };

                var keys = Object.keys(attributesHash);

                keys.forEach(function (key) {
                    if (attributesHash[key] == "")
                        delete attributesHash[key];
                });

                return self.$create_image_block(parent, $hash2(Object.keys(attributesHash), attributesHash))
                    ;
            }, nil) && 'process';
    })(self, ($scope.Extensions)._scope.BlockMacroProcessor);
    return (function ($base, $super) {
        function $ChartBlockProcessor() {
        };
        var self = $ChartBlockProcessor = $klass($base, $super, 'ChartBlockProcessor', $ChartBlockProcessor);

        var def = self._proto, $scope = self._scope;

        self.$use_dsl();

        self.$named("chart");

        self.$on_context("open");

        self.$parse_content_as("literal");

        return (def.$process = function (parent, reader, attrs) {
                console.log("ChartBlockProcessor");
                var $a, self = this, target = nil, type = nil, title = nil, filename = nil, alt = nil, caption = nil, width = nil, height = nil, opt = nil, scale = nil, align = nil, cache = nil, chartType = nil, imagesdir = nil;

                chartType = "" + (attrs['$[]']("2"));
                title = "" + (attrs['$[]']("title"));
                alt = "" + (attrs['$[]']("alt"));
                caption = "" + (attrs['$[]']("caption"));
                width = "" + (attrs['$[]']("width"));
                height = "" + (attrs['$[]']("height"));
                scale = "" + (attrs['$[]']("scale"));
                align = "" + (attrs['$[]']("align"));
                type = "" + (attrs['$[]']("type"));
                filename = "" + (attrs['$[]']("file"));
                cache = "" + (attrs['$[]']("cache"));
                opt = "" + (attrs['$[]']("opt"));
                imagesdir = parent.$document().$attr('imagesdir', '');

                target = parent.$image_uri(filename);

                if (cache != "enabled") {
                    afx.chartBuild(reader.$read(), imagesdir, target, chartType, opt);
                }

                var attributesHash = {
                    "target": filename,
                    "title": title,
                    "alt": alt,
                    "caption": caption,
                    "width": width,
                    "height": height,
                    "scale": scale,
                    "align": align,
                    "opt": opt
                };

                var keys = Object.keys(attributesHash);

                keys.forEach(function (key) {
                    if (attributesHash[key] == "")
                        delete attributesHash[key];
                });

                return self.$create_image_block(parent, $hash2(Object.keys(attributesHash), attributesHash))
                    ;
            }, nil) && 'process';
    })(self, ($scope.Extensions)._scope.BlockProcessor);
})(Opal);
/* Generated by Opal 0.6.3 */
(function ($opal) {
    var $a, $b, TMP_1, self = $opal.top, $scope = $opal, nil = $opal.nil, $breaker = $opal.breaker, $slice = $opal.slice;

    if ($scope.RUBY_ENGINE['$==']("opal")) {
    }
    ;
    return ($a = ($b = $scope.Extensions).$register, $a
            .
            _p = (TMP_1 = function () {
                var self = TMP_1._s || this, $a;

                self.$block_macro($scope.ChartBlockMacro);
                return self.$block($scope.ChartBlockProcessor);

            }, TMP_1
                .
                _s = self, TMP_1
        ),
            $a
    ).
        call($b);
})(Opal);
