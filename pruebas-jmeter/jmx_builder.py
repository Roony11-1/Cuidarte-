import xml.etree.ElementTree as ET
from pathlib import Path


class JmxPlanBuilder:
    """Constructor fluido sobre XML ElementTree para planes de prueba Apache JMeter 5.6.3."""

    def __init__(self, plan_name: str, serialize_thread_groups: bool = True) -> None:
        self.root = ET.Element(
            "jmeterTestPlan",
            {"version": "1.2", "properties": "5.0", "jmeter": "5.6.3"},
        )
        root_ht = ET.SubElement(self.root, "hashTree")

        test_plan = ET.SubElement(
            root_ht,
            "TestPlan",
            {
                "guiclass": "TestPlanGui",
                "testclass": "TestPlan",
                "testname": plan_name,
                "enabled": "true",
            },
        )
        self._add_prop(test_plan, "boolProp", "TestPlan.functional_mode", "false")
        self._add_prop(
            test_plan,
            "boolProp",
            "TestPlan.serialize_threadgroups",
            "true" if serialize_thread_groups else "false",
        )
        self.plan_tree = ET.SubElement(root_ht, "hashTree")

    def add_thread_group(
        self,
        name: str,
        tg_type: str = "normal",
        threads: int = 1,
        loops: int = 1,
        ramp_time: int = 1,
    ) -> ET.Element:
        tag_map = {
            "setup": ("SetupThreadGroup", "SetupThreadGroupGui"),
            "normal": ("ThreadGroup", "ThreadGroupGui"),
            "teardown": ("PostThreadGroup", "PostThreadGroupGui"),
        }
        if tg_type not in tag_map:
            raise ValueError(f"Tipo de ThreadGroup no soportado: {tg_type}")

        tag, gui = tag_map[tg_type]
        tg = ET.SubElement(
            self.plan_tree,
            tag,
            {"guiclass": gui, "testclass": tag, "testname": name, "enabled": "true"},
        )
        self._add_prop(tg, "stringProp", "ThreadGroup.on_sample_error", "continue")

        loop_ctrl = ET.SubElement(
            tg,
            "elementProp",
            {
                "name": "ThreadGroup.main_controller",
                "elementType": "LoopController",
                "guiclass": "LoopControlPanel",
                "testclass": "LoopController",
                "enabled": "true",
            },
        )
        self._add_prop(loop_ctrl, "boolProp", "LoopController.continue_forever", "false")
        self._add_prop(loop_ctrl, "stringProp", "LoopController.loops", str(loops))

        self._add_prop(tg, "stringProp", "ThreadGroup.num_threads", str(threads))
        self._add_prop(tg, "stringProp", "ThreadGroup.ramp_time", str(ramp_time))
        self._add_prop(tg, "boolProp", "ThreadGroup.scheduler", "false")

        return ET.SubElement(self.plan_tree, "hashTree")

    def add_header_manager(self, parent_tree: ET.Element, name: str, headers: dict[str, str]) -> None:
        hm = ET.SubElement(
            parent_tree,
            "HeaderManager",
            {
                "guiclass": "HeaderPanel",
                "testclass": "HeaderManager",
                "testname": name,
                "enabled": "true",
            },
        )
        coll = ET.SubElement(hm, "collectionProp", {"name": "HeaderManager.headers"})
        for key, value in headers.items():
            header_ep = ET.SubElement(coll, "elementProp", {"name": "", "elementType": "Header"})
            self._add_prop(header_ep, "stringProp", "Header.name", key)
            self._add_prop(header_ep, "stringProp", "Header.value", value)
        ET.SubElement(parent_tree, "hashTree")

    def add_http_sampler(
        self,
        parent_tree: ET.Element,
        name: str,
        method: str,
        path: str,
        body: str | None = None,
    ) -> ET.Element:
        sampler = ET.SubElement(
            parent_tree,
            "HTTPSamplerProxy",
            {
                "guiclass": "HttpTestSampleGui",
                "testclass": "HTTPSamplerProxy",
                "testname": name,
                "enabled": "true",
            },
        )
        args_ep = ET.SubElement(
            sampler,
            "elementProp",
            {"name": "HTTPsampler.Arguments", "elementType": "Arguments"},
        )
        args_coll = ET.SubElement(args_ep, "collectionProp", {"name": "Arguments.arguments"})

        if body is not None:
            self._add_prop(sampler, "boolProp", "HTTPSampler.postBodyRaw", "true")
            arg = ET.SubElement(
                args_coll, "elementProp", {"name": "", "elementType": "HTTPArgument"}
            )
            self._add_prop(arg, "boolProp", "HTTPArgument.always_encode", "false")
            self._add_prop(arg, "stringProp", "Argument.value", body)
            self._add_prop(arg, "stringProp", "Argument.metadata", "=")

        self._add_prop(sampler, "stringProp", "HTTPSampler.domain", "${__P(api_host,cuidarteplus-backend-ev)}")
        self._add_prop(sampler, "stringProp", "HTTPSampler.port", "${__P(api_port,4000)}")
        self._add_prop(sampler, "stringProp", "HTTPSampler.protocol", "${__P(api_protocol,http)}")
        self._add_prop(sampler, "stringProp", "HTTPSampler.path", path)
        self._add_prop(sampler, "stringProp", "HTTPSampler.method", method)
        self._add_prop(sampler, "boolProp", "HTTPSampler.follow_redirects", "true")
        self._add_prop(sampler, "boolProp", "HTTPSampler.auto_redirects", "false")
        self._add_prop(sampler, "boolProp", "HTTPSampler.use_keepalive", "true")
        self._add_prop(sampler, "boolProp", "HTTPSampler.DO_MULTIPART_POST", "false")

        return ET.SubElement(parent_tree, "hashTree")

    def add_status_assertion(self, sampler_tree: ET.Element, expected_code: int) -> None:
        ra = ET.SubElement(
            sampler_tree,
            "ResponseAssertion",
            {
                "guiclass": "AssertionGui",
                "testclass": "ResponseAssertion",
                "testname": f"Assert HTTP {expected_code}",
                "enabled": "true",
            },
        )
        coll = ET.SubElement(ra, "collectionProp", {"name": "Asserion.test_strings"})
        self._add_prop(coll, "stringProp", "code", str(expected_code))
        self._add_prop(ra, "stringProp", "Assertion.test_field", "Assertion.response_code")
        self._add_prop(ra, "boolProp", "Assertion.assume_success", "false")
        self._add_prop(ra, "intProp", "Assertion.test_type", "8")
        ET.SubElement(sampler_tree, "hashTree")

    def add_json_extractor(
        self,
        sampler_tree: ET.Element,
        name: str,
        var_name: str,
        json_path: str,
        default_value: str = "NOT_FOUND",
    ) -> None:
        jp = ET.SubElement(
            sampler_tree,
            "JSONPostProcessor",
            {
                "guiclass": "JSONPostProcessorGui",
                "testclass": "JSONPostProcessor",
                "testname": name,
                "enabled": "true",
            },
        )
        self._add_prop(jp, "stringProp", "JSONPostProcessor.referenceNames", var_name)
        self._add_prop(jp, "stringProp", "JSONPostProcessor.jsonPathExprs", json_path)
        self._add_prop(jp, "stringProp", "JSONPostProcessor.match_numbers", "1")
        self._add_prop(jp, "stringProp", "JSONPostProcessor.defaultValues", default_value)
        ET.SubElement(sampler_tree, "hashTree")

    def add_groovy_postprocessor(self, sampler_tree: ET.Element, name: str, script: str) -> None:
        jsr = ET.SubElement(
            sampler_tree,
            "JSR223PostProcessor",
            {
                "guiclass": "TestBeanGUI",
                "testclass": "JSR223PostProcessor",
                "testname": name,
                "enabled": "true",
            },
        )
        self._add_prop(jsr, "stringProp", "scriptLanguage", "groovy")
        self._add_prop(jsr, "stringProp", "script", script)
        ET.SubElement(sampler_tree, "hashTree")

    def save(self, output_path: str | Path) -> Path:
        target = Path(output_path)
        target.parent.mkdir(parents=True, exist_ok=True)
        ET.indent(self.root, space="  ")
        ET.ElementTree(self.root).write(target, encoding="utf-8", xml_declaration=True)
        return target

    @staticmethod
    def _add_prop(parent: ET.Element, tag: str, name: str, value: str) -> ET.Element:
        element = ET.SubElement(parent, tag, {"name": name})
        element.text = str(value)
        return element